#!/bin/sh
# current time minus 61 seconds (latest file we want to move)
# used as a filename and timestamp for a file (used for a find(8) comparison)
#
# Thanks to http://www.cepheid.org/~jeff/?p=30
#
STAMP=`date --date="61 seconds ago" +%Y%m%d%H%M.%S`
# where rotatelogs is writing
SDIR=/var/log/httpd
# where flume is picking up
DDIR=/home/cloudera/logs/flumelog
# debugging
# echo Date:  `date`
# echo Using: $STAMP
# Create our comparison file
touch -t $STAMP $SDIR/$STAMP
# move older logs to flume spooldir
find $SDIR -name al_flume.* ! -newer $SDIR/$STAMP -exec mv {} $DDIR/ \;
# cleanup touchfile

chmod 777 $DDIR/*.*

rm $SDIR/$STAMP
