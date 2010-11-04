package org.eobjects.analyzer.storage;

import org.eobjects.analyzer.util.ReflectionUtils;

/**
 * Hsqldb based implementation of the StorageProvider.
 * 
 * @author Kasper Sørensen
 * 
 */
public final class HsqldbStorageProvider extends SqlDatabaseStorageProvider implements StorageProvider {

	public HsqldbStorageProvider() {
		super("org.hsqldb.jdbcDriver", "jdbc:hsqldb:mem:analyzerbeans", "SA", "");
	}

	@Override
	protected String getSqlType(Class<?> valueType) {
		if (String.class == valueType) {
			return "VARCHAR";
		}
		if (Integer.class == valueType) {
			return "INTEGER";
		}
		if (Long.class == valueType) {
			return "BIGINT";
		}
		if (Double.class == valueType) {
			return "DOUBLE";
		}
		if (Short.class == valueType) {
			return "SHORT";
		}
		if (Float.class == valueType) {
			return "FLOAT";
		}
		if (Character.class == valueType) {
			return "CHAR";
		}
		if (Boolean.class == valueType) {
			return "BOOLEAN";
		}
		if (Byte.class == valueType) {
			return "BINARY";
		}
		if (ReflectionUtils.isByteArray(valueType)) {
			return "BLOB";
		}
		throw new UnsupportedOperationException("Unsupported value type: " + valueType);
	}

	@Override
	public RowAnnotationFactory createRowAnnotationFactory() {
		// TODO: Create a persistent RowAnnotationFactory
		return new InMemoryRowAnnotationFactory();
	}

}