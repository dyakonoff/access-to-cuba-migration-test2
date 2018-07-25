package com.haulmont.cuba.core.sys.persistence;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * DbmsFeatures implementation for Microsoft Access.
 */
// TODO: Fix DB Features for MS ACCESS, PASS 1 - OK
@SuppressWarnings("unused")
public class AccessDbmsFeatures implements DbmsFeatures {

    @Override
    public Map<String, String> getJpaParameters() {
        // TODO: ??? what shall be done?
        HashMap<String, String> params = new HashMap<>();
        params.put("eclipselink.target-database", "com.haulmont.cuba.core.sys.persistence.CubaSQLServerPlatform");
        return params;
    }

    @Override
    public String getIdColumn() {
        return "ID";
    }

    @Override
    public String getDeleteTsColumn() {
        return "DELETE_TS";
    }

    @Override
    public String getTimeStampType() {
        return "DATETIME";
    }

    @Nullable
    @Override
    public String getUuidTypeClassName() {
        return null;
    }

    @Nullable
    @Override
    public String getTransactionTimeoutStatement() {
        return null;
    }

    @Override
    public String getUniqueConstraintViolationPattern() {
        return "with unique index '(.+)'";
    }

    @Override
    public boolean isNullsLastSorting() {
        return false;
    }

    @Override
    public boolean isSchemaByUser() {
        return false;
    }

    @Override
    public boolean supportsLobSortingAndFiltering() {
        return true;
    }
}