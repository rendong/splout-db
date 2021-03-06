package com.splout.db.hadoop;

/*
 * #%L
 * Splout SQL Hadoop library
 * %%
 * Copyright (C) 2012 Datasalt Systems S.L.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.fs.Path;

import com.datasalt.pangool.io.Fields;
import com.datasalt.pangool.io.Schema;
import com.datasalt.pangool.tuplemr.OrderBy;
import com.datasalt.pangool.tuplemr.mapred.lib.input.TupleTextInputFormat;
import com.splout.db.hadoop.TableBuilder.TableBuilderException;
import com.splout.db.hadoop.TablespaceBuilder.TablespaceBuilderException;

/**
 * A JSON-friendly bean that can map to a {@link TablespaceSpec} easily. It only supports text input. Use
 * {@link #build()} for obtaining a TablespaceSpec instance.
 * <p/>
 * For specifying compound indexes use "," separator in the string index definition.
 */
public class JSONTablespaceDefinition {

  private String name;
  private int nPartitions;
  private List<JSONTableDefinition> partitionedTables;
  private List<JSONTableDefinition> replicateAllTables = new ArrayList<JSONTableDefinition>();

  /*
  * Inner static method for converting a {@link JSONTableDefinition} into a {@link Table} bean through {@link TableBuilder}.
  */
  private static Table buildTable(JSONTableDefinition table, boolean isReplicateAll) throws TableBuilderException {
    if (table.getName() == null) {
      throw new IllegalArgumentException("Must provide a name for all tables.");
    }
    if (table.getSchema() == null) {
      throw new IllegalArgumentException("Must provide an schema for all tables.");
    }
    if (!isReplicateAll && (table.getPartitionFields() == null || table.getPartitionFields() == null || table.getPartitionFields().length() == 0)) {
      throw new IllegalArgumentException("Partitioned table must be partitioned by some field.");
    }
    if (table.getTableInputs() == null || table.getTableInputs().size() == 0) {
      throw new IllegalArgumentException("Table must have some table inputs.");
    }

    Schema schema = new Schema(table.getName(), Fields.parse(table.getSchema()));
    TableBuilder tableBuilder = new TableBuilder(schema);
    for (String index : table.getIndexes()) {
      tableBuilder.createIndex(index.split(","));
    }

    if (!isReplicateAll) {
      tableBuilder.partitionBy(table.getPartitionFields().split(","));
    }

    // Adding pre and post SQL
    if (table.getInitialStatements().size() != 0) {
      tableBuilder.preInsertsSQL(table.getInitialStatements().toArray(new String[0]));
    }
    if (table.getPreInsertStatements().size() != 0) {
      tableBuilder.preInsertsSQL(table.getPreInsertStatements().toArray(new String[0]));
    }
    if (table.getPostInsertStatements().size() != 0) {
      tableBuilder.preInsertsSQL(table.getPostInsertStatements().toArray(new String[0]));
    }
    if (table.getFinalStatements().size() != 0) {
      tableBuilder.finalSQL(table.getFinalStatements().toArray(new String[0]));
    }

    if(table.getInsertionOrderBy() != null) {
    	tableBuilder.insertionSortOrder(OrderBy.parse(table.getInsertionOrderBy()));
    }
    
    for (JSONTableInputDefinition tableInput : table.getTableInputs()) {
      TextInputSpecs specs = tableInput.getInputSpecs();
      if (specs == null) {
        specs = new TextInputSpecs(); // default specs (tabulated file)
      }
      if (tableInput.getPaths() == null || tableInput.getPaths().size() == 0) {
        throw new IllegalArgumentException("All table inputs must have input paths.");
      }

      for (String file : tableInput.getPaths()) {
        if (specs.getFixedWidthFields() != null) {
          // Fixed width fields definition.
          int[] fieldsArr = new int[specs.getFixedWidthFields().size()];
          for (int i = 0; i < fieldsArr.length; i++) {
            fieldsArr[i] = specs.getFixedWidthFields().get(i);
          }
          tableBuilder.addFixedWidthTextFile(new Path(file), schema, fieldsArr, specs.isSkipHeader(), specs.getNullString(), null);
        } else {
          // CSV definition
          tableBuilder.addCSVTextFile(file, specs.getSeparatorChar(), specs.getQuotesChar(), specs.getEscapeChar(),
              specs.isSkipHeader(), specs.isStrictQuotes(), specs.getNullString());

        }
      }
    }

    if (isReplicateAll) {
      tableBuilder.replicateToAll();
    }
    return tableBuilder.build();
  }

  /**
   * Use this method for obtaining a {@link TablespaceSpec} through {@link TablespaceBuilder}.
   */
  public TablespaceSpec build() throws TablespaceBuilderException, TableBuilderException {
    TablespaceBuilder builder = new TablespaceBuilder();
    builder.setNPartitions(nPartitions);

    if (partitionedTables == null) {
      throw new IllegalArgumentException("Can't build a " + TablespaceSpec.class.getName()
          + " without any partitioned table.");
    }

    if (name == null) {
      throw new IllegalArgumentException("Must provide a name for the Tablespace.");
    }

    for (JSONTableDefinition table : partitionedTables) {
      builder.add(buildTable(table, false));
    }

    for (JSONTableDefinition table : replicateAllTables) {
      builder.add(buildTable(table, true));
    }

    return builder.build();
  }

  public static class JSONTableDefinition {

    private String name;
    private List<JSONTableInputDefinition> tableInputs;
    private String schema;
    private String partitionFields;
    private String insertionOrderBy;
    private List<String> indexes = new ArrayList<String>();
    private List<String> initialStatements = new ArrayList<String>();
    private List<String> preInsertStatements = new ArrayList<String>();
    private List<String> postInsertStatements = new ArrayList<String>();
    private List<String> finalStatements = new ArrayList<String>();

    public List<JSONTableInputDefinition> getTableInputs() {
      return tableInputs;
    }

    public void setTableInputs(List<JSONTableInputDefinition> tableInputs) {
      this.tableInputs = tableInputs;
    }

    public String getSchema() {
      return schema;
    }

    public void setSchema(String schema) {
      this.schema = schema;
    }

    public String getPartitionFields() {
      return partitionFields;
    }

    public void setPartitionFields(String partitionFields) {
      this.partitionFields = partitionFields;
    }

    public List<String> getIndexes() {
      return indexes;
    }

    public void setIndexes(List<String> indexes) {
      this.indexes = indexes;
    }

    public List<String> getInitialStatements() {
      return initialStatements;
    }

    public void setInitialStatements(List<String> initialStatements) {
      this.initialStatements = initialStatements;
    }

    public List<String> getPostInsertStatements() {
      return postInsertStatements;
    }

    public void setPostInsertStatements(List<String> postInsertStatements) {
      this.postInsertStatements = postInsertStatements;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<String> getPreInsertStatements() {
      return preInsertStatements;
    }

    public void setPreInsertStatements(List<String> preInsertStatements) {
      this.preInsertStatements = preInsertStatements;
    }

    public List<String> getFinalStatements() {
      return finalStatements;
    }

    public void setFinalStatements(List<String> finalStatements) {
      this.finalStatements = finalStatements;
    }

		String getInsertionOrderBy() {
    	return insertionOrderBy;
    }

		void setInsertionOrderBy(String insertionOrderBy) {
    	this.insertionOrderBy = insertionOrderBy;
    }
  }

  public static class JSONTableInputDefinition {

    private TextInputSpecs inputSpecs;
    private List<String> paths;

    public TextInputSpecs getInputSpecs() {
      return inputSpecs;
    }

    public void setInputSpecs(TextInputSpecs inputSpecs) {
      this.inputSpecs = inputSpecs;
    }

    public List<String> getPaths() {
      return paths;
    }

    public void setPaths(List<String> paths) {
      this.paths = paths;
    }
  }

  public static class TextInputSpecs {

    private char separatorChar = '\t';
    private char quotesChar = TupleTextInputFormat.NO_QUOTE_CHARACTER;
    private char escapeChar = TupleTextInputFormat.NO_ESCAPE_CHARACTER;
    private boolean skipHeader = false;
    private boolean strictQuotes = false;
    private String nullString = TupleTextInputFormat.NO_NULL_STRING;
    private ArrayList<Integer> fixedWidthFields = null;

    public char getSeparatorChar() {
      return separatorChar;
    }

    public void setSeparatorChar(char separatorChar) {
      this.separatorChar = separatorChar;
    }

    public char getQuotesChar() {
      return quotesChar;
    }

    public void setQuotesChar(char quotesChar) {
      this.quotesChar = quotesChar;
    }

    public char getEscapeChar() {
      return escapeChar;
    }

    public void setEscapeChar(char escapeChar) {
      this.escapeChar = escapeChar;
    }

    public boolean isSkipHeader() {
      return skipHeader;
    }

    public void setSkipHeader(boolean skipHeader) {
      this.skipHeader = skipHeader;
    }

    public boolean isStrictQuotes() {
      return strictQuotes;
    }

    public void setStrictQuotes(boolean strictQuotes) {
      this.strictQuotes = strictQuotes;
    }

    public String getNullString() {
      return nullString;
    }

    public void setNullString(String nullString) {
      this.nullString = nullString;
    }

    public ArrayList<Integer> getFixedWidthFields() {
      return fixedWidthFields;
    }

    public void setFixedWidthFields(ArrayList<Integer> fixedWidthFields) {
      this.fixedWidthFields = fixedWidthFields;
    }
  }

  public int getnPartitions() {
    return nPartitions;
  }

  public void setnPartitions(int nPartitions) {
    this.nPartitions = nPartitions;
  }

  public List<JSONTableDefinition> getPartitionedTables() {
    return partitionedTables;
  }

  public void setPartitionedTables(List<JSONTableDefinition> partitionedTables) {
    this.partitionedTables = partitionedTables;
  }

  public List<JSONTableDefinition> getReplicateAllTables() {
    return replicateAllTables;
  }

  public void setReplicateAllTables(List<JSONTableDefinition> replicateAllTables) {
    this.replicateAllTables = replicateAllTables;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
