package com.hbase.log.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.FlumeException;
import org.hbase.async.AtomicIncrementRequest;
import org.hbase.async.PutRequest;
import org.apache.flume.conf.ComponentConfiguration;
import org.apache.flume.sink.hbase.SimpleHbaseEventSerializer.KeyType;
import org.apache.flume.sink.hbase.AsyncHbaseEventSerializer;

import com.google.common.base.Charsets;
/**
 * A serializer for the AsyncHBaseSink, which splits the event body into
 * multiple columns and inserts them into a row whose key is available in
 * the headers
 *
 * Originally from https://blogs.apache.org/flume/entry/streaming_data_into_apache_hbase
 * Modified by Dan Sandler
 */
public class AsyncHbaseLogEventSerializer implements AsyncHbaseEventSerializer {
  private byte[] table;
  private byte[] colFam;
  private Event currentEvent;
  private byte[][] columnNames;
  private final List<PutRequest> puts = new ArrayList<PutRequest>();
  private final List<AtomicIncrementRequest> incs = new ArrayList<AtomicIncrementRequest>();
  private byte[] currentRowKey;
  private final byte[] eventCountCol = "eventCount".getBytes();
  private String delim;

  @Override
  public void initialize(byte[] table, byte[] cf) {
    this.table = table;
    this.colFam = cf;
  }

  @Override
  public void setEvent(Event event) {
    // Set the event and verify that the rowKey is not present
    this.currentEvent = event;
    String rowKeyStr = currentEvent.getHeaders().get("rowKey");
    //if (rowKeyStr == null) {
    //  throw new FlumeException("No row key found in headers!");
    //}
    //currentRowKey = rowKeyStr.getBytes();
  }

  @Override
  public List<PutRequest> getActions() {
    // Split the event body and get the values for the columns
    String eventStr = new String(currentEvent.getBody());
    //String[] cols = eventStr.split(",");
    //String[] cols = eventStr.split(regEx);
    //String[] cols = eventStr.split("\\s+");
    //String[] cols = eventStr.split("\\t");
    String[] cols = eventStr.split(delim);
    puts.clear();
    String[] columnFamilyName;
    byte[] bCol;
    byte[] bFam;
    for (int i = 0; i < cols.length; i++) {
      //Generate a PutRequest for each column.
      columnFamilyName = new String(columnNames[i]).split(":");
      bFam = columnFamilyName[0].getBytes();
      bCol = columnFamilyName[1].getBytes();

      if (i == 0) {
         currentRowKey = cols[i].getBytes();
      }
      //PutRequest req = new PutRequest(table, currentRowKey, colFam,
              //columnNames[i], cols[i].getBytes());
      PutRequest req = new PutRequest(table, currentRowKey, bFam,
              bCol, cols[i].getBytes());
      puts.add(req);
    }
    return puts;
  }

  @Override
  public List<AtomicIncrementRequest> getIncrements() {
    incs.clear();
    //Increment the number of events received
    incs.add(new AtomicIncrementRequest(table, "totalEvents".getBytes(), colFam, eventCountCol));
    return incs;
  }

  @Override
  public void cleanUp() {
    table = null;
    colFam = null;
    currentEvent = null;
    columnNames = null;
    currentRowKey = null;
  }

  @Override
  public void configure(Context context) {
    //Get the column names from the configuration
    String cols = new String(context.getString("columns"));
    String[] names = cols.split(",");
    columnNames = new byte[names.length][];
    int i = 0;
    for(String name : names) {
      columnNames[i++] = name.getBytes();
    }
    delim = new String(context.getString("delimiter"));
  }

  @Override
  public void configure(ComponentConfiguration conf) {
  }
}
