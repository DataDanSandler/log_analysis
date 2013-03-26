How To setup Hadoop To Stream Apache Logs to HBase Via Flume 
===================

This repository contains sample code to configure Flume to stream Apache web logs to HBase on Hadoop.  It includes components such as:

1) The Flume configuratione

2) The Apache Configuration

3) A custom Java implementation of AsyncHbaseEventSerializer 

4) A rotate log script

5) DDL the HBase table 

Assumes you have working knowledge of these technical components.

Step 1: Deploy HBase Table
---------------
1) **Confirm hbase-site.xml configuration**
	1) vi /etc/hbase/conf/hbase-site.xml
	
	2) Confirm rootdir has proper hostname
	
	3) Repeat for
	
		a. Core-site.xml
		
		b. Hive-site.xml

2) **Make sure HBase services started**

   a) service hbase-master stop/start
   
   b) service hbase-regionserver stop/start

3) **Create HBase table**

   a) sudo -u hdfs hbase shell
   
         For help "help<enter>"
         
	 Ex: help 'create'

   b) create 'apache_access_log', {NAME => 'common'}, {NAME => 'http'}, {NAME => 'misc'}, {NAME => 'geoip_common'}, {NAME => 'geoip_country'}, {NAME => 'geoip_city'}
   
Step 1a: Download Geo IP MaxMind Libraries For Apache
---------------
Reference http://dev.maxmind.com/geoip/mod_geoip2 

0) yum install httpd-devel apr-devel
1) sudo yum install GeoIP GeoIP-devel GeoIP-data zlib-devel
2) cd  /home/cloudera/logs/geoip
3) wget http://www.maxmind.com/download/geoip/api/c/GeoIP-latest.tar.gz 
4) wget http://www.maxmind.com/download/geoip/api/mod_geoip2/mod_geoip2-latest.tar.gz 
5) wget http://geolite.maxmind.com/download/geoip/database/GeoLiteCity.dat.gz 
6) wget http://download.maxmind.com/download/geoip/database/asnum/GeoIPASNum.dat.gz 
7) tar -zxvf mod_geoip2-latest.tar.gz
8) tar -zxvf GeoIP-latest.tar.gz
9) gunzip GeoLiteCity.dat.gz
10) gunzip  GeoIPASNum.dat.gz
11) mv *.dat GeoIP-1.5.0/data
12) cd GeoIP-1.5.0
13) ./configure && make && make install
14) mkdir /usr/local/share/GeoIP
15) cd -
16) cp GeoIP-1.5.0/data/*.dat  /usr/local/share/GeoIP
17) cp mod_geoip2_1.2.8/mod_geoip.c  /usr/lib64/httpd/modules
18) cd mod_geoip2_1.2.8
19) apxs -i -a -L/usr/lib64 -I/usr/include -lGeoIP -c mod_geoip.c
20) vi /etc/ld.so.conf
	a. Add :
		/usr/local/lib
	b. Save
	c. Then run "ldconfig"
21) Add the following to httpd.conf:
	
#GeoIP library
	
<IfModule mod_geoip.c>
  GeoIPEnable On
  GeoIPDBFile /home/cloudera/logs/geoip/GeoIP-1.5.0/data/GeoIP.dat MemoryCache
  GeoIPDBFile /home/cloudera/logs/geoip/GeoIP-1.5.0/data/GeoLiteCity.dat MemoryCache
  GeoIPDBFile /home/cloudera/logs/geoip/GeoIP-1.5.0/data/GeoIPASNum.dat MemoryCache
  GeoIPEnableUTF8 On  
  GeoIPScanProxyHeaders On
 GeoIPUseLastXForwardedForIP On
</IfModule>


Step 2: Apache Log File format and rotation
---------------

1) **Install apache**

   a) yum install httpd
  
   b) vi /etc/httpd/conf/httpd.conf
   
   c) Add the following at the end to create custom log that is easily processed / parseable
 
 	# Thanks to http://www.cepheid.org/~jeff/?p=30 
 	# logs for Flume injestion. Custom log format for easier regex matching
 	LogFormat "%{UNIQUE_ID}e	%v	%h	%l	%u	%{%Y%m%d%H%M%S}t	%r	%>s	%b	%{Referer}i	%{User-Agent}i	%{GEOIP_COUNTRY_CODE}e  %{GEOIP_ADDR}e	%{GEOIP_CONTINENT_CODE}e	%{GEOIP_COUNTRY_NAME}e	%{GEOIP_REGION}e	%{GEOIP_REGION_NAME}e	%{GEOIP_CITY}e	%{GEOIP_METRO_CODE}e	%{GEOIP_AREA_CODE}e	%{GEOIP_LATITUDE}e	%{GEOIP_LONGITUDE}e	%{GEOIP_POSTAL_CODE}e" vfcombined
 	CustomLog "| /usr/sbin/rotatelogs /var/log/httpd/al_flume.%Y%m%d%H%M%S 60" vfcombined

   d)  Uncomment
       
       a. #LoadModule unique_id_module modules/mod_unique_id.so
  
   d) chkconfig httpd on
   
   e) /etc/init.d/httpd restart
   
2) **Create Log Rotation Script**

See http://www.cepheid.org/~jeff/?p=30 for original script

   a) mkdir /home/cloudera/logs
   
   b) mkdir /home/cloudera/logs/flumelog
   
   c) Create script flume_rotate.sh in /home/cloudera/logs, make sure permissions are X for root
   
   <pre>
   	#!/bin/sh
   	# current time minus 61 seconds (latest file we want to move)
   	# used as a filename and timestamp for a file (used for a find(8) comparison)
   	# Thanks to http://www.cepheid.org/~jeff/?p=30
   	STAMP=`date --date="61 seconds ago" "+%Y%m%d%H%M.%S"`
   	# where rotatelogs is writing
   	SDIR=/var/log/httpd
   	# where flume is picking up
   	DDIR=/var/log/httpd/flumelog
   	# debugging
   	# echo "Date: " `date`
   	# echo "Using: $STAMP"
   	# Create our comparison file
   	touch -t $STAMP $SDIR/$STAMP
   	# move older logs to flume spooldir
   	find $SDIR -name ‘al_flume.*’ ‘!’ -newer $SDIR/$STAMP -exec mv "{}" $DDIR/ \;
   	# cleanup touchfile
   	rm $SDIR/$STAMP
   </pre>

3) Schedule every 60 seconds in cron

    a. crontab -e
    
        * * * * * /home/cloudera/logs/flume_rotate.sh

Step 3: Create Custom Event Serializer
---------------

This custom Java class takes the log event from the serialized Flume source and maps each log field to an HBase column family

See https://blogs.apache.org/flume/entry/streaming_data_into_apache_hbase for original SplitteSerializer, which was modified for our purposes

1) Create Java Class

   a) cd /home/cloudera/logs/
   
   b) mkdir com; mkdir com/hbase; mkdir com/hbase/log; mkdir com/hbase/log/util
   
   c) vi com/hbase/log/util/AsyncHbaseLogSerializer.java
   
   d) see Java code under com/hbase/log/util of this repository, paste contents
   
2) Compile Java Class

   a)  export CLASSPATH=/usr/lib/flume-ng/lib/flume-ng-core-1.3.0.jar:/usr/lib/flume-ng/lib/flume-ng-sdk-1.3.0.jar:/usr/lib/flume-ng/lib/flume-thrift-source-1.2.0-cdh4.1.3.jar:/usr/lib/flume-ng/lib/flume-scribe-source-1.2.0-cdh4.1.3.jar:/usr/lib/flume-ng/lib/flume-hdfs-sink-1.2.0-cdh4.1.3.jar:/usr/lib/flume-ng/lib/flume-ng-node-1.2.0-cdh4.1.3.jarflume-ng-log4jappender-1.2.0-cdh4.1.3.jar:/usr/lib/flume-ng/lib/flume-avro-source-1.2.0-cdh4.1.3.jar:/usr/lib/flume-ng/lib/flume-irc-sink-1.2.0-cdh4.1.3.jar:/usr/lib/flume-ng/lib/flume-ng-configuration-1.2.0-cdh4.1.3.jar:/usr/lib/flume-ng/lib/flume-file-channel-1.2.0-cdh4.1.3.jar:/usr/lib/hadoop/flume-sources-1.0-SNAPSHOT.jar:/usr/lib/flume-ng/lib/asynchbase-1.2.0.jar:/usr/lib/hive/lib/hbase.jar:/usr/lib/flume-ng/lib/flume-ng-hbase-sink-1.2.0-cdh4.1.3.jar:/usr/lib/flume-ng/lib/guava-11.0.2.jar
   
   b) javac  com/hbase/log/util/AsyncHbaseLogEventSerializer.java
   
   c) jar cf LogEventUtil.jar com
   
   d) jar tf LogEventUtil.jar com
   
   e) chmod 775 LogEventUtil.jar
   
   f) cp  LogEventUtil.jar /usr/lib/flume-ng/lib


Step 4: Configure Flume
---------------

 1) See flume-conf/flume.conf for proper values
 
 2) Make sure you have flume core and flume ng sdk 1.3.0 or greater
 
 3) Also need to download and add the folloiwng missing jars
 
		 cp flume-ng-core-1.3.0.jar /usr/lib/flume-ng/lib/
		 
		 cp /home/cloudera/facebook/apache-flume-1.3.0-bin/lib/flume-ng-sdk-1.3.0.jar /usr/lib/flume-ng/lib
		 

4) /etc/init.d/flume-ng-agent stop

5) /etc/init.d/flume-ng-agent start

6) make sure everything looks good in /var/log/flume-ng/flume.log

