select value_column referring_agent, count(distinct id) cnt
from (
SELECT
  a.apache_access_log_id id,
  a.name_column,
  a.value_column,
  'Common' ColumnFamily
FROM
  `HBaseLogAnalysis`.`apache_access_log_common` a
 union all  
 select 
  a1.apache_access_log_id,
  a1.name_column,
  a1.value_column,
  'HTTP' ColumnFamily
from apache_access_log_http a1
union all 
SELECT
  a.apache_access_log_id,
  a.name_column,
  a.value_column,
  'Misc' ColumnFamily
FROM
  `HBaseLogAnalysis`.`apache_access_log_misc` a
union all 
SELECT
  a.apache_access_log_id,
  a.name_column,
  a.value_column,
  'GeoipCountry' ColumnFamily
FROM
  `HBaseLogAnalysis`.`apache_access_log_geoip_country` a
union all 
SELECT
  a.apache_access_log_id,
  a.name_column,
  a.value_column,
  'GeoipCommon' ColumnFamily
FROM
  `HBaseLogAnalysis`.`apache_access_log_geoip_common` a
union all 
SELECT
  a.apache_access_log_id,
  a.name_column,
  a.value_column,
  'GeoipCity' ColumnFamily
FROM
  `HBaseLogAnalysis`.`apache_access_log_geoip_city` a
  ) d
  where ColumnFamily='GeoipCity' and 
  name_column='geoip_city_name'
  group by value_column;
