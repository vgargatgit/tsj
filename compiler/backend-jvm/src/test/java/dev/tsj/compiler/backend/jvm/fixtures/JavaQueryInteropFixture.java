package dev.tsj.compiler.backend.jvm.fixtures;

import java.util.List;

public final class JavaQueryInteropFixture {
    public Query query(final Class<?> entityType) {
        return new Query(entityType);
    }

    public static final class Query {
        private final Class<?> entityType;
        private String filterValue = "";

        public Query(final Class<?> entityType) {
            this.entityType = entityType;
        }

        public Query setParameter(final String name, final Object value) {
            if ("lastName".equals(name)) {
                this.filterValue = String.valueOf(value);
            }
            return this;
        }

        public List<ResultRow> getResultList() {
            return List.of(new ResultRow(entityType.getSimpleName(), filterValue));
        }
    }

    public static final class ResultRow {
        private final String entity;
        private final String filter;

        public ResultRow(final String entity, final String filter) {
            this.entity = entity;
            this.filter = filter;
        }

        public String getEntity() {
            return entity;
        }

        public String getFilter() {
            return filter;
        }
    }
}
