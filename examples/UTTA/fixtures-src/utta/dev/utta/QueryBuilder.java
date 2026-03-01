package dev.utta;

/**
 * Builder pattern (fluent API) for interop testing.
 */
public final class QueryBuilder {
    private String table = "";
    private String whereClause = "";
    private String orderBy = "";
    private int limit = -1;

    public QueryBuilder from(String table) { this.table = table; return this; }
    public QueryBuilder where(String clause) { this.whereClause = clause; return this; }
    public QueryBuilder orderBy(String field) { this.orderBy = field; return this; }
    public QueryBuilder limit(int n) { this.limit = n; return this; }

    public String build() {
        StringBuilder sb = new StringBuilder("SELECT * FROM ").append(table);
        if (!whereClause.isEmpty()) sb.append(" WHERE ").append(whereClause);
        if (!orderBy.isEmpty()) sb.append(" ORDER BY ").append(orderBy);
        if (limit > 0) sb.append(" LIMIT ").append(limit);
        return sb.toString();
    }
}
