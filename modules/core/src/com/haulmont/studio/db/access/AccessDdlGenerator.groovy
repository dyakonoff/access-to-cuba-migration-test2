package com.haulmont.studio.db.access

import java.sql.DatabaseMetaData
import groovy.sql.Sql

/**
 * This class is used by Studio at design time for working with Microsoft Access.
 * It provides a set of properties and methods for generation of database init and update scripts,
 * and for creating model from an existing database.
 */
// TODO: fix DDL generator helper class for MS ACCESS
@SuppressWarnings("GroovyUnusedDeclaration")
class AccessDdlGenerator {

    /**
     * An object that contains helper methods and default implementations.
     */
    DdlGeneratorDelegate delegate

    /**
     * Map of attribute's Java type to an SQL column type.
     * <p>
     * The key is a simple class name, e.g. 'Boolean', 'byte[]', the value is column type, e.g. 'tinyint', 'image'.
     */
    Map<String, String> types = [
            'Boolean' : 'bit',
            'byte[]' : 'binary',
            'Character' : 'character',
            'Date' : 'datetime',
            'BigDecimal' : 'decimal',
            'Double' : 'double',
            'Integer' : 'integer',
            'Long' : 'decimal',
            'String' : 'varchar',
            'UUID' : 'uniqueidentifier'
    ]

    /**
     * Map of temporal type names to column types.
     * <p>
     * The key is one of the following strings: TIMESTAMP, DATE or TIME.
     * The value is a column type, e.g. 'timestamp'.
     */
    Map<String, String> temporalTypes = [
            'DATE' : 'datetime',
            'TIME' : 'datetime',
            'TIMESTAMP' : 'datetime'
    ]

    /**
     * Map of column types that can be converted without removing and creating a new column,
     * but solely by executing 'alter column'.
     * For example:
     * <pre>
     * text -> varchar, nvarchar, ntext
     * bigint -> int4, int, integer, int identity, smallint
     * </pre>
     * The key is a column type, the value is a collection of column types.
     */
    Map<String, List<String>> convertibleTypes = [:]

    /**
     * Map of attribute's Java type to an SQL column default value.
     * <p>
     * The key is is a simple class name, the value is a default value.
     */
    Map<String, String> defaultValues = [
            'Boolean' : '0',
            'ByteArray' : "''",
            'Date' : "'=Now()'",
            'DateTime' : "'=Now()'",
            'Time' : "'=Now()'",
            'BigDecimal' : '0',
            'Double' : '0',
            'Integer' : '0',
            'Long' : '0',
            'String' : "''",
            'UUID' : 'newid()'
    ]
    /**
     * Collection of column type synonyms. A synonym is a column type that can be used for the same Java type.
     * <p>
     * This collection is used when Studio determines whether to generate an update script for a column with changed
     * Java type in the model. For example:
     * <ol>
     * <li>Attribute has changed from String to Integer. Studio picks 'int' type from {@link #types} collection and checks
     * whether the previous 'varchar' type and the new 'int' type are synonyms. They are not, so Studio generates
     * statements for dropping and adding a column with the new type.
     * <li>For a String attribute, a developer has changed type in the database to 'nvarchar'. When Studio generates scripts,
     * it sees that the default 'varchar' type and 'nvarchar' are synonyms, and does not generate updates for the column.
     * </ol>
     */
    List<List<String>> typeSynonyms = [
            // 1 byte long booleans and integers
            ['tinyint', 'integer1', 'byte', 'bit', 'boolean', 'logical', 'logical1', 'yesno'],
            // binary objects and OLE objects
            ['binary', 'varbinary', 'binary varying', 'bit varying', 'varbinary(max)', 'image',
             'longbinary', 'general', 'oleobject'],
            // stored as 8 bytes doubles
            ['datetime', 'smalldatetime', 'date', 'time'],
            // 0 - 255 chars, 2 bytes per character
            ['char', 'alphanumeric', 'character', 'string', 'varchar', 'character varying', 'nchar',
             'national character', 'national char', 'national character varying', 'national char varying',
             'test(max)'],
            // decimal - 17 bytes & money - 8 bytes
            ['decimal', 'dec', 'numeric', 'money', 'smallmoney', 'currency'],
            // 4 abd 8 bytes float numbers
            ['float', 'double', 'float8', 'ieeedouble', 'number', 'real', 'single', 'float4', 'ieeesingle'],
            // 2 and 4 bytes ints
            ['integer', 'long', 'int', 'integer4', 'smallint', 'short', 'integer2'],
            // 0 to 2.14 GB length, 2 bytes per character
            ['text', 'longtext', 'longchar', 'memo', 'note', 'ntext'],
            ['uniqueidentifier', 'guid']
    ]

    /**
     * DBMS reserved words.
     */
    List<String> reservedKeywords = [
            'ABSOLUTE', 'ADD', 'ADMINDB', 'ALL', 'Alphanumeric', 'ALTER', 'ALTER TABLE', 'And', 'ANY', 'ARE',
            'AS', 'AS', 'ASC', 'ASSERTION', 'AUTHORIZATION', 'AUTOINCREMENT', 'Avg', 'BEGIN', 'Between', 'BINARY',
            'BIT', 'BIT_LENGTH', 'BOOLEAN', 'BOTH', 'BY', 'BYTE', 'CASCADE', 'CATALOG', 'CHAR', 'CHAR_LENGTH',
            'CHARACTER', 'CHARACTER_LENGTH', 'CHECK', 'CLOSE', 'CLUSTERED', 'COALESCE', 'COLLATE', 'COLLATION',
            'COLUMN', 'COMMIT', 'COMP', 'COMPRESSION', 'CONNECT', 'CONNECTION', 'CONSTRAINT', 'CONSTRAINTS',
            'CONTAINER', 'CONTAINS', 'CONVERT', 'Count', 'COUNTER', 'CREATE', 'CURRENCY', 'CURRENT_DATE',
            'CURRENT_TIME', 'CURRENT_TIMESTAMP', 'CURRENT_USER', 'CURSOR', 'DATABASE', 'DATE', 'DATETIME',
            'DAY', 'DEC', 'DECIMAL', 'DECLARE', 'DELETE', 'DESC', 'DISALLOW', 'DISCONNECT', 'DISTINCT',
            'DISTINCTROW', 'DOMAIN', 'DOUBLE', 'DROP', 'Eqv', 'EXCLUSIVECONNECT', 'EXEC', 'EXECUTE', 'EXISTS',
            'EXTRACT', 'FALSE', 'FETCH', 'FIRST', 'FLOAT', 'FLOAT4', 'FLOAT8', 'FOREIGN', 'FROM', 'FROM',
            'GENERAL', 'GRANT', 'GROUP', 'GUID', 'HAVING', 'HOUR', 'IDENTITY', 'IEEEDOUBLE', 'IEEESINGLE',
            'IGNORE', 'IMAGE', 'Imp', 'IN', 'In', 'INDEX', 'INDEXCREATEDB', 'INNER', 'INPUT', 'INSENSITIVE',
            'INSERT', 'INSERT INTO', 'INT', 'INTEGER', 'INTEGER1', 'INTEGER2', 'INTEGER4', 'INTERVAL', 'INTO',
            'Is', 'ISOLATION', 'JOIN', 'KEY', 'LANGUAGE', 'LAST', 'LEFT', 'Level' /*###*/, 'Like', 'LOGICAL',
            'LOGICAL1', 'LONG', 'LONGBINARY', 'LONGCHAR', 'LONGTEXT', 'LOWER', 'MATCH', 'Max', 'MEMO',
            'Min', 'MINUTE', 'Mod', 'MONEY', 'MONTH', 'NATIONAL', 'NCHAR', 'NONCLUSTERED', 'Not', 'NTEXT',
            'NULL', 'NUMBER', 'NUMERIC', 'NVARCHAR', 'OCTET_LENGTH', 'OLEOBJECT', 'ON', 'OPEN', 'OPTION',
            'Or', 'ORDER', 'Outer' /*###*/, 'OUTPUT', 'OWNERACCESS', 'PAD', 'PARAMETERS', 'PARTIAL', 'PASSWORD',
            'PERCENT', 'PIVOT', 'POSITION', 'PRECISION', 'PREPARE', 'PRIMARY', 'PRIVILEGES', 'PROC', 'PROCEDURE',
            'PUBLIC', 'REAL', 'REFERENCES', 'RESTRICT', 'REVOKE', 'RIGHT', 'ROLLBACK', 'SCHEMA', 'SECOND',
            'SELECT', 'SELECTSCHEMA', 'SELECTSECURITY', 'SET', 'SHORT', 'SINGLE', 'SIZE', 'SMALLDATETIME',
            'SMALLINT', 'SMALLMONEY', 'SOME', 'SPACE', 'SQL', 'SQLCODE', 'SQLERROR', 'SQLSTATE', 'StDev',
            'StDevP', 'STRING', 'SUBSTRING', 'Sum', 'SYSNAME', 'SYSTEM_USER', 'TABLE', 'TableID' /*###*/,
            'TEMPORARY', 'TEXT', 'TIME', 'TIMESTAMP', 'TIMEZONE_HOUR', 'TIMEZONE_MINUTE', 'TINYINT', 'TO',
            'TOP', 'TRAILING', 'TRANSACTION', 'TRANSFORM', 'TRANSLATE', 'TRANSLATION', 'TRIM', 'TRUE', 'UNION',
            'UNIQUE', 'UNIQUEIDENTIFIER', 'UNKNOWN', 'UPDATE', 'UPDATEIDENTITY', 'UPDATEOWNER', 'UPDATESECURITY',
            'UPPER', 'USAGE', 'USER', 'USING', 'VALUE', 'VALUES', 'Var', 'VARBINARY', 'VARCHAR', 'VarP',
            'VARYING', 'VIEW', 'WHEN', 'WHENEVER', 'WHERE', 'WITH', 'WORK', 'Xor', 'YEAR', 'YESNO', 'ZONE'
    ]

    /**
     * Collection of column types that do not support parameters.
     * <p>
     * Studio generates column definitions by information from {@link DatabaseMetaData}, which always contains
     * column length. But many types do not support explicit definining of length, for example 'int' type has length
     * 2147483647, but `mycol int(2147483647)` won't work.
     */
    List<String> noParameterTypes = [
            'tinyint', 'integer1', 'byte', 'bit', 'boolean', 'logical', 'logical1', 'yesno',
            'datetime', 'smalldatetime', 'date', 'time',
            'decimal', 'dec', 'numeric', 'money', 'smallmoney', 'currency',
            'float', 'double', 'float8', 'ieeedouble', 'number', 'real', 'single', 'float4', 'ieeesingle',
            'integer', 'long', 'int', 'integer4', 'smallint', 'short', 'integer2',
            'uniqueidentifier', 'guid',
            'image', 'longbinary', 'general', 'oleobject'
    ]

    /**
     * Columns of these types are filled in automatically by DBMS and not mapped to a Java type.
     */
    List<String> automaticallyGeneratedTypes = [
            'counter',
            'autoincrement'
    ]

    /**
     * Returns "drop table" statement.
     *
     * @param metaData  JDBC metadata
     * @param schema    database schema
     * @param tableName table name
     * @return a statement
     */
    String getDropTableStatement(DatabaseMetaData metaData, String schema, String tableName) {
        "drop table $tableName ^"
    }

    /**
     * Returns the list of statements for removing constraints before dropping a table.
     * <p>
     * If the database removes constraints automatically, the method should return an empty list.
     *
     * @param metaData  JDBC metadata
     * @param schema    database schema
     * @param tableName table name
     * @return a list of statements
     */
    List<String> getDropTableConstraintStatements(DatabaseMetaData metaData, String schema, String tableName) {
        List<String> result = []
        // String dbName = delegate.getMainDataStoreDatabaseName()
        def connection = delegate.getConnection()
        try {
            new Sql(connection).eachRow("select CONSTRAINT_NAME from INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
                    "where CONSTRAINT_CATALOG = 'PUBLIC' and TABLE_NAME = ? and CONSTRAINT_TYPE = 'FOREIGN KEY'",
                    [dbName, tableName]) { row ->
                result.add(getDropConstraintStatement(tableName, row['CONSTRAINT_NAME'].toUpperCase()))
            }
        } finally {
            connection.close()
        }
        return result
    }

    /**
     * Returns names of unique and non-unique indexes for a given column.
     * <p>
     * Deafult implementation: {@link DdlGeneratorDelegate#defaultGetColumnIndexNames(java.sql.DatabaseMetaData, java.lang.String, java.lang.String, java.lang.String)}
     *
     * @param metaData      java.sql.DatabaseMetaData object
     * @param schema        database schema
     * @param tableName     table name
     * @param columnName    column name
     * @return collection of index names
     */
    Collection<String> getColumnIndexNames(DatabaseMetaData metaData, String schema, String tableName, String columnName) {
        Set<String> indexes = new LinkedHashSet<>()
        new Sql(metaData.getConnection()).eachRow(
                "SELECT indexinfo.INDEX_NAME, indexinfo.NON_UNIQUE " +
                        "FROM INFORMATION_SCHEMA.SYSTEM_INDEXINFO  indexinfo " +
                        "  JOIN UCA_METADATA.COLUMNS_VIEW colview ON " +
                        "    indexinfo.TABLE_NAME = colview.ESCAPED_TABLE_NAME AND " +
                        "    indexinfo.COLUMN_NAME = colview.ESCAPED_COLUMN_NAME " +
                        "WHERE colview.TABLE_NAME = ? and colview.COLUMN_NAME = ?",
                [tableName, columnName]) { row ->
            String indexName = row['INDEX_NAME'].toUpperCase()
            boolean isUnique = !row['NON_UNIQUE']
            if (isUnique != 1 || !indexName.startsWith(INDEX_PREFIX + delegate.getProjectNamespace().toUpperCase())) {
                //unique index will be removed in generateUpdateIndex() method
                indexes.add(indexName)
            }
        }
        return indexes
    }

    /**
     * Returns names of constraints for a given column.
     * <p>
     * Default implementation: {@link DdlGeneratorDelegate#defaultGetColumnConstraintNames(java.sql.DatabaseMetaData, java.lang.String, java.lang.String, java.lang.String)}
     *
     * @param metaData      java.sql.DatabaseMetaData object
     * @param schema        database schema
     * @param tableName     table name
     * @param columnName    column name
     * @return collection of constraint names
     */
    Collection<String> getColumnConstraintNames(DatabaseMetaData metaData, String schema, String tableName,
                                                String columnName) {
        // TODO
        Set<String> constraints = new LinkedHashSet<>()
        String dbName = delegate.getMainDataStoreDatabaseName()
        new Sql(metaData.getConnection()).eachRow("select * from INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE " +
                "where CONSTRAINT_CATALOG = ? and TABLE_NAME = ? and COLUMN_NAME = ?",
                [dbName, tableName, columnName]) { row ->
            constraints.add(row["CONSTRAINT_NAME"])
        }
        new Sql(metaData.getConnection()).eachRow("select dc.NAME" +
                " from sys.TABLES t" +
                " inner join sys.DEFAULT_CONSTRAINTS dc on t.OBJECT_ID = dc.PARENT_OBJECT_ID" +
                " inner join sys.COLUMNS c on dc.PARENT_OBJECT_ID = c.OBJECT_ID and c.COLUMN_ID = dc.PARENT_COLUMN_ID" +
                " where t.NAME = ? and c.NAME = ?", [tableName, columnName]) { row ->
            constraints.add(row["NAME"])
        }
        return constraints;
    }

    /**
     * Returns "drop column" statement.
     *
     * @param tableName     table name
     * @param columnName    column name
     * @return  a statement
     */
    String getDropColumnStatement(String tableName, String columnName) {
        "alter table $tableName drop column ${delegate.wrapColumnInQuotesIfNeeded(columnName)} ^"
    }

    /**
     * Returns the list of statements for dropping all constraints for a column.
     * <p>
     * Default implementation: {@link DdlGeneratorDelegate#defaultGetDropColumnConstraintStatements(java.lang.String, java.lang.String)}
     *
     * @param tableName     table name
     * @param columnName    column name
     * @return list of statements
     */
    List<String> getDropColumnConstraintStatements(String tableName, String columnName) {
        delegate.defaultGetDropColumnConstraintStatements(tableName, columnName)
    }

    /**
     * Returns column name for inserting to "add column" script.
     * <p>
     * Default implementation: {@link DdlGeneratorDelegate#defaultPrepareColumnForAddScript(java.lang.String)}
     *
     * @param columnName
     * @return column name
     */
    String prepareColumnForAddScript(String columnName) {
        return delegate.defaultPrepareColumnForAddScript(columnName)
    }

    /**
     * Returns a statement for adding a column to a table creating it from entity and attribute objects.
     *
     * @param entity    entity object
     * @param attribute attribute object
     * @param column    column name
     * @return "alter table add column" statement
     */
    String getAddColumnStatement(def entity, def attribute, String column) {
        if (!attribute.isEmbedded()) {
            StringBuilder sb = new StringBuilder()
            String col = delegate.getColumnDefinition(entity, attribute, column.toUpperCase(), attribute.isMandatory() && attribute.isClass())
            sb.append("alter table ${entity.table} add $col ^")
            delegate.processScriptAddingMandatoryColumn(sb, entity, attribute, column.empty ? attribute.getDdlManipulationColumn() : column)
            return sb.toString()
        } else {
            return delegate.getAddColumnsForEmbeddedAttrStatements(entity, attribute, Collections.emptyList())
        }
    }

    /**
     * Returns a statement for adding a column to a table creating it from table and column names passed as strings.
     *
     * @param tableName     table name
     * @param columnName    column name
     * @param type          attribute type object with the following properties: packageName, className, fqn, label
     * @param notNull       column nullability
     * @return "alter table add column" statement
     */
    String getAddColumnStatement(String tableName, String columnName, def type, boolean notNull) {
        return "alter table ${tableName} add ${columnName} ${delegate.defaultGetColumnType(type.className)} ${notNull ? 'not null ' : ''} ^"
    }

    /**
     * Returns a statement for changing string column length. Used only for String attributes.
     *
     * @param entity        entity object
     * @param attribute     attribute object
     * @param newType       new type name
     * @param columnName    column name
     * @return "alter table alter column" statement
     */
    String getAlterColumnStringLengthStatement(def entity, def attribute, String newType, String columnName) {
        String col = delegate.getColumnName(attribute, columnName)
        Integer length = delegate.getColumnLength(entity, attribute)
        String newLength = (length != null && length != 0) ? length.toString() : "max"
        return "alter table ${entity.table} alter column ${col} ${newType}(${newLength}) ^"
    }

    /**
     * Returns a statement for changing decimal column parameters. Used only for BigDecimal attributes.
     *
     * @param entity        entity object
     * @param attribute     attribute object
     * @param newType       new type name
     * @param columnName    column name
     * @return "alter table alter column" statement
     */
    String getAlterColumnDecimalParamsStatement(def entity, def attribute, String newType, String columnName) {
        String col = delegate.getColumnName(attribute, columnName)
        String params = delegate.getDecimalParams(entity, attribute)
        return "alter table ${entity.table} alter column ${col} ${newType}(${params}) ^"
    }

    /**
     * Returns a statement for changing column type.
     *
     * @param entity        entity object
     * @param attribute     attribute object
     * @param newType       new type name
     * @param columnName    column name
     * @return "alter table alter column" statement
     */
    String getAlterColumnTypeStatement(def entity, def attribute, String newType, String columnName) {
        String col = delegate.getColumnName(attribute, columnName)
        return "alter table ${entity.table} alter column ${col} ${newType} ^"
    }

    /**
     * Returns a statement for changing column nullability.
     *
     * @param tableName     table name
     * @param columnName    column name
     * @param columnDef     column parameters
     * @param isMandatory   true if not null
     * @return "alter table alter column" statement
     */
    String getAlterColumnMandatoryStatement(String tableName, String columnName, String columnDef, boolean isMandatory) {
        return "alter table ${tableName} alter column ${columnName} ${columnDef} ${isMandatory ? 'not null' : ''} ^"
    }

    /**
     * Returns a statement for changing column nullability.
     *
     * @param entity        entity object
     * @param attribute     attribute object
     * @param columnName    column name
     * @return "alter table alter column" statement
     */
    String getAlterColumnMandatoryStatement(def entity, def attribute, String columnName) {
        if (!attribute.isId()) {
            String col = delegate.getColumnDefinition(entity, attribute, columnName, true)
            return "alter table ${entity.table} alter column ${col} ^"
        }
        return ""
    }

    /**
     * Returns a statement for renaming table.
     *
     * @param oldTable old table name
     * @param newTable new table name
     * @return a statement
     */
    String getRenameTableSatement(String oldTable, String newTable) {
        return "sp_rename '${oldTable}', '${newTable}' ^\n"
    }

    /**
     * Returns a statement for renaming column.
     *
     * @param table     table name
     * @param oldColumn old column name
     * @param newColumn new column name
     * @return a statement
     */
    String getRenameColumnStatement(String table, String oldColumn, String newColumn) {
        return "exec sp_rename '$table.$oldColumn', '$newColumn', 'COLUMN' ^\n"
    }

    /**
     * For a BigDecimal attribute, returns true if the precision of the attribute differs from the precision of the
     * database column.
     *
     * @param attributePrecision    attribute precision
     * @param columnPrecision       column precision
     * @param columnType            column type
     * @return true if the precision is different
     */
    boolean isPrecisionDifferent(int attributePrecision, int columnPrecision, String columnType) {
        if ('money'.equals(columnType)) {
            return attributePrecision != 19
        }
        if ('smallmoney'.equals(columnType)) {
            return attributePrecision != 10
        }
        //default precision for decimal column in mssql is 18
        return columnPrecision != attributePrecision && !(columnPrecision == 18 && attributePrecision == 0)
    }

    /**
     * For a BigDecimal attribute, returns true if the scale of the attribute differs from the scale of the
     * database column.
     * <p>
     * Default implementation: {@link DdlGeneratorDelegate#defaultIsScaleDifferent(int, int, java.lang.String)}
     *
     * @param attributeScale    attribute scale
     * @param columnScale       column scale
     * @param columnType        column type
     * @return true/false
     */
    Boolean isScaleDifferent(int attributeScale, int columnScale, String columnType) {
        if ('money'.equals(columnType) || 'smallmoney'.equals(columnType)) {
            return attributeScale != 4
        }
        return columnScale != attributeScale && (columnScale > -84 || attributeScale == 0)
    }

    /**
     * Returns a statement for primary key definition.
     * <p>
     * Default implementation: {@link DdlGeneratorDelegate#defaultGetPrimaryKeyStatement(java.lang.Object)}
     *
     * @param entity entity object
     * @return a statement
     */
    String getPrimaryKeyStatement(def entity) {
        String primaryKeyColumn = delegate.getPrimaryKeyColumn(entity).toUpperCase()
        return delegate.getIdType(entity) == 'UUID' ?
            "primary key nonclustered (${primaryKeyColumn})" :
            "primary key (${primaryKeyColumn})"
    }

    /**
     * Returns a statement for making an existing column a primary key.
     * <p>
     * Default implementation: {@link DdlGeneratorDelegate#defaultGetPrimaryKeyConstraintStatement(java.lang.Object, boolean)}
     *
     * @param entity   entity object
     * @param notNull  whether to add "not null" to the column
     * @return a statement
     */
    String getPrimaryKeyConstraintStatement(def entity, boolean notNull) {
        def idAttr = delegate.getIdAttribute(entity)
        String idColumnName = entity.getColumnName(idAttr)

        String notNullStatement = ""
        def idType = delegate.getIdType(entity)
        if (!idType == 'LongIdentity' && !idType == 'IntegerIdentity') {
            String col = delegate.getColumnDefinition(entity, idAttr, idColumnName, true)
            notNullStatement = notNull ?
                    "alter table ${entity.table} alter column ${col} not null ^\n" : ""
        }
        String nonClustered = idType == 'UUID' ? 'nonclustered ' : ''
        return notNullStatement +
                "alter table ${entity.table} add constraint PK_${entity.table} primary key ${nonClustered}(${idColumnName}) ^"
    }

    /**
     * Returns true if the current column type is different and not compatible with the old one.
     * <p>
     * Default implementation: {@link DdlGeneratorDelegate#defaultIsTypeDifferent(java.lang.Object, java.lang.String, java.lang.String, int)}
     *
     * @param attribute     attribute object
     * @param currentType   current column type
     * @param oldType       old column type
     * @param oldLength     old column length
     * @return true if different
     */
    Boolean isTypeDifferent(def attribute, String currentType, String oldType, int oldLength) {
        if (("char".equals(oldType) ||  "nchar".equals(oldType)) && "varchar".equals(currentType) && oldLength > 1)
            return false
        else
            return delegate.defaultIsTypeDifferent(attribute, currentType, oldType, oldLength)
    }

    /**
     * Returns a statement for creating a sequence.
     *
     * @param sequenceName  sequence name
     * @param startValue    start value
     * @param increment     increment
     * @return a statement
     */
    String getCreateSequenceStatement(String sequenceName, long startValue, long increment) {
        "create table ${sequenceName.toUpperCase()} (ID bigint identity(${startValue},${increment}), CREATE_TS datetime) ;"
    }

    /**
     * Returns a statement for checking if a sequence with the given name exists in the database.
     *
     * @param sequenceName name of sequence
     * @return a statement
     */
    String getSequenceExistsStatement(String sequenceName) {
        "select * from INFORMATION_SCHEMA.TABLES where TABLE_NAME = '${sequenceName.toUpperCase()}' ;"
    }

    /**
     * Returns a statement for deleting a sequence with the given name from the database.
     *
     * @param sequenceName name of sequence
     * @return a statement
     */
    String getDeleteSequenceStatement(String sequenceName) {
        return "drop table ${(sequenceName != null ? sequenceName.toUpperCase() : null)} ;";
    }

    /**
     * Returns a maximum length of a string type (varchar and all its synonyms).
     * By default, it is {@link Integer#MAX_VALUE}.
     *
     * @param columnType    column type
     * @return max length
     */
    Integer getMaxVarcharLength(String columnType) {
        return (["nchar", "nvarchar", "ntext"].contains(columnType)) ? 1073741823 : Integer.MAX_VALUE
    }

    /**
     * Returns long identity column type.
     *
     * @return type name
     */
    String getLongIdentityType() {
        return 'bigint identity'
    }

    /**
     * Returns integer identity column type.
     *
     * @return type name
     */
    String getIntegerIdentityType() {
        return 'int identity'
    }

    /**
     * Returns the part of a column definition containing parameters and (optional) "not null" clause.
     * Parameters must be enclosed in parentheses.
     * The returning string is used when a table is created and when a column is added or altered.
     *
     * @param entity        entity object
     * @param attribute     attribute object
     * @param notNull       whether to add "not null"
     * @return parameters string
     */
    String getColumnParameters(def entity, def attribute, boolean notNull) {
        if (attribute.isId() || attribute.isClass()) {
            return getPrimaryOrForeignKeyParams(attribute)
        }

        StringBuilder params = new StringBuilder()
        def fqn = attribute?.type?.fqn
        //length
        if (fqn == 'java.lang.String' || fqn == 'java.lang.Character') {
            Integer length = delegate.getColumnLength(entity, attribute)
            if (length != null)
                params.append('(').append(length).append(')')
            else
                params.append("(max)")
        } else if (attribute.isEnum() && attribute.type?.type?.id == 'String') {
            //enum string length
            params.append("(").append(delegate.getDefaultEnumColumnLength()).append(")")
        }

        delegate.processBigDecimalParams(attribute, params)
        //not null
        if (attribute.mandatory && notNull) {
            params.append(" not null")
        }
        return params.toString()
    }

    /**
     * For the given column type, returns 'DATE', 'TIME' or 'TIMESTAMP' strings indicating what attribute
     * type should be used for the column.
     * <p>
     * Default implementation: {@link DdlGeneratorDelegate#defaultGetTemporalType(java.lang.String)}
     *
     * @param columnType    column type
     * @return 'DATE', 'TIME', 'TIMESTAMP' or null
     */
    String getTemporalType(String columnType) {
        if ("date".equalsIgnoreCase(columnType)) {
            return 'DATE'
        } else if ("time".equalsIgnoreCase(columnType)) {
            return 'TIME'
        }
        if (temporalTypes.find { it.value == columnType }) {
            return 'TIMESTAMP'
        }
        for (String columnTypeSynonym : getColumnTypeSynonyms(columnType) ) {
            if (temporalTypes.find { it.value == columnTypeSynonym }) {
                return 'TIMESTAMP'
            }
        }
        return null
    }

    /**
     * Returns "drop index" statement.
     * For example:
     * <pre>drop index ${constraintName.toUpperCase()} ^</pre>
     *
     * @param tableName    table name
     * @param indexName    index name
     * @return "drop index" statement
     */
    String getDropIndexStatement(String tableName, String indexName) {
        "drop index ${indexName.toUpperCase()} on ${tableName} ^"
    }

    /**
     * Returns statement for dropping a foreign key constraint.
     *
     * @param tableName         table name
     * @param constraintName    constraint name
     * @return a statement
     */
    String getDropConstraintStatement(String tableName, String constraintName) {
        "alter table $tableName drop constraint ${constraintName.toUpperCase()} ^\n"
    }

    /**
     * Returns column type for a given attribute.
     * <p>
     * Default implementation: {@link DdlGeneratorDelegate#defaultGetColumnType(java.lang.String)}
     *
     * @param entity    entity object
     * @param attribute attribute object
     * @return column type
     */
    String getColumnType(def entity, def attr) {
        delegate.defaultGetColumnType(attr.type.className)
    }

    /**
     * Returns column type for a given attribute if it is a primary or foreign key.
     * Default implementation: {@link DdlGeneratorDelegate#defaultGetPrimaryOrForeignKeyType(java.lang.Object)}
     *
     * @param attribute attribute object
     * @return column type for a primary or foreign key
     */
    String getPrimaryOrForeignKeyType(def attribute) {
        delegate.defaultGetPrimaryOrForeignKeyType(attribute)
    }

    /**
     * Returns the part of a primary or foreign key column definition containing parameters
     * and (optional) "not null" clause.
     * Parameters must be enclosed in parentheses.
     * <p>
     * Default implementation: {@link DdlGeneratorDelegate#defaultGetPrimaryOrForeignKeyParams(java.lang.Object)}
     *
     * @param attribute attribute object
     * @return parameters string
     */
    String getPrimaryOrForeignKeyParams(def attribute) {
        delegate.defaultGetPrimaryOrForeignKeyParams(attribute)
    }

    /**
     * Returns collection of synonyms of the given column type.
     *
     * @param columnType    column type
     * @return collection of synonyms
     */
    Collection<String> getColumnTypeSynonyms(String columnType) {
        def synonyms = typeSynonyms.find { it.contains(columnType) }
        return synonyms ?: []
    }

    /**
     * Whether to include DELETE_TS column in a unique index for soft deleted entities.
     * Return false if the database does not support uniqueness for records containing null,
     * as in HSQLDB.
     */
    boolean isDeleteTsInUniqueIndex() {
        return true
    }

    /**
     * (non-Javadoc)
     *
     * @see {@link DdlGeneratorDelegate#defaultReplaceDelimiter(java.lang.String)}
     */
    String replaceDelimiter(String script) {
        delegate.defaultReplaceDelimiter(script)
    }

    /**
     * @return ddl script with creating functions and database objects.
     * The script will be executed before other generate model scripts.
     */
    String generateImportInitScript() {
        return null
    }

    /**
     * Whether to separately drop scripts when generate update DB.
     * The property can be disabled in the Studio settings page.
     */
    boolean isGenerateSeparatelyDropScripts() {
        return true
    }
}