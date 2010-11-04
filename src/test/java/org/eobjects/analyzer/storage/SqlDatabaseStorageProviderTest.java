package org.eobjects.analyzer.storage;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.InputRow;
import org.eobjects.analyzer.data.MockInputColumn;
import org.eobjects.analyzer.data.MockInputRow;

public class SqlDatabaseStorageProviderTest extends TestCase {

	private H2StorageProvider sp = new H2StorageProvider();

	public void testCreateList() throws Exception {
		List<String> list = sp.createList(String.class);
		assertEquals(0, list.size());
		assertTrue(list.isEmpty());

		list.add("hello");
		list.add("world");
		assertEquals(2, list.size());

		assertEquals("world", list.get(1));

		assertEquals("[hello, world]", Arrays.toString(list.toArray()));

		list.remove(1);

		assertEquals("[hello]", Arrays.toString(list.toArray()));

		list.remove("foobar");
		list.remove("hello");

		assertEquals("[]", Arrays.toString(list.toArray()));
	}

	public void testCreateMap() throws Exception {
		Map<Integer, String> map = sp.createMap(Integer.class, String.class);

		map.put(1, "hello");
		map.put(2, "world");
		map.put(5, "foo");

		assertEquals("world", map.get(2));
		assertNull(map.get(3));

		assertEquals(3, map.size());

		// override 5
		map.put(5, "bar");

		assertEquals(3, map.size());
	}

	public void testCreateSet() throws Exception {
		Set<Long> set = sp.createSet(Long.class);

		assertTrue(set.isEmpty());
		assertEquals(0, set.size());

		set.add(1l);

		assertEquals(1, set.size());

		set.add(2l);
		set.add(3l);

		assertEquals(3, set.size());

		set.add(3l);

		assertEquals(3, set.size());

		Iterator<Long> it = set.iterator();
		assertTrue(it.hasNext());
		assertEquals(Long.valueOf(1l), it.next());

		assertTrue(it.hasNext());
		assertEquals(Long.valueOf(2l), it.next());

		assertTrue(it.hasNext());
		assertEquals(Long.valueOf(3l), it.next());

		assertFalse(it.hasNext());

		assertFalse(it.hasNext());

		it = set.iterator();

		assertTrue(it.hasNext());
		assertEquals(Long.valueOf(1l), it.next());

		// remove 1
		it.remove();

		assertTrue(it.hasNext());
		assertEquals(Long.valueOf(2l), it.next());

		assertTrue(it.hasNext());
		assertEquals(Long.valueOf(3l), it.next());

		assertFalse(it.hasNext());

		assertEquals("[2, 3]", Arrays.toString(set.toArray()));
	}

	public void testFinalize() throws Exception {
		Connection connectionMock = EasyMock.createMock(Connection.class);
		Statement statementMock = EasyMock.createMock(Statement.class);

		EasyMock.expect(connectionMock.createStatement()).andReturn(statementMock);
		EasyMock.expect(statementMock.executeUpdate("CREATE TABLE MY_TABLE (set_value VARCHAR PRIMARY KEY)")).andReturn(0);
		EasyMock.expect(statementMock.isClosed()).andReturn(false);
		statementMock.close();

		EasyMock.expect(connectionMock.createStatement()).andReturn(statementMock);
		EasyMock.expect(statementMock.executeUpdate("DROP TABLE MY_TABLE")).andReturn(0);
		EasyMock.expect(statementMock.isClosed()).andReturn(false);
		statementMock.close();

		EasyMock.replay(statementMock, connectionMock);

		SqlDatabaseSet<String> set = new SqlDatabaseSet<String>(connectionMock, "MY_TABLE", "VARCHAR");
		assertEquals(0, set.size());
		set = null;
		System.gc();
		System.runFinalization();

		EasyMock.verify(statementMock, connectionMock);
	}

	public void testCreateRowAnnotationFactory() throws Exception {
		RowAnnotationFactory f = sp.createRowAnnotationFactory();

		RowAnnotation a1 = f.createAnnotation();
		RowAnnotation a2 = f.createAnnotation();

		InputColumn<String> col1 = new MockInputColumn<String>("foo", String.class);
		InputColumn<Integer> col2 = new MockInputColumn<Integer>("bar", Integer.class);
		InputColumn<Boolean> col3 = new MockInputColumn<Boolean>("w00p", Boolean.class);

		MockInputRow row1 = new MockInputRow(1).put(col1, "1").put(col2, 1).put(col3, true);
		MockInputRow row2 = new MockInputRow(2).put(col1, "2");
		MockInputRow row3 = new MockInputRow(3).put(col1, "3").put(col2, 3).put(col3, true);
		MockInputRow row4 = new MockInputRow(4).put(col1, "4").put(col2, 4).put(col3, false);

		InputRow[] rows = f.getRows(a1);
		assertEquals(0, rows.length);

		f.annotate(row1, 3, a1);
		assertEquals(3, a1.getRowCount());

		rows = f.getRows(a1);
		assertEquals(1, rows.length);
		assertEquals("1", rows[0].getValue(col1));
		assertEquals(Integer.valueOf(1), rows[0].getValue(col2));
		assertEquals(Boolean.TRUE, rows[0].getValue(col3));

		// repeat the same annotate call - should do nothing
		f.annotate(row1, 3, a1);
		assertEquals(3, a1.getRowCount());

		assertEquals(1, rows.length);
		assertEquals("1", rows[0].getValue(col1));

		f.annotate(row2, 2, a1);
		f.annotate(row2, 2, a1);
		f.annotate(row2, 2, a1);
		f.annotate(row2, 2, a1);
		assertEquals(5, a1.getRowCount());

		rows = f.getRows(a1);
		assertEquals(2, rows.length);
		assertEquals("1", rows[0].getValue(col1));
		assertEquals(Integer.valueOf(1), rows[0].getValue(col2));
		assertEquals(Boolean.TRUE, rows[0].getValue(col3));
		assertEquals("2", rows[1].getValue(col1));
		assertEquals(null, rows[1].getValue(col2));
		assertEquals(null, rows[1].getValue(col3));

		assertEquals(0, a2.getRowCount());

		f.annotate(row1, 3, a2);

		assertEquals(5, a1.getRowCount());
		assertEquals(3, a2.getRowCount());

		f.annotate(row3, 6, a2);
		f.annotate(row4, 7, a2);

		assertEquals(5, a1.getRowCount());
		assertEquals(16, a2.getRowCount());

		rows = f.getRows(a1);
		assertEquals(2, rows.length);

		rows = f.getRows(a2);
		assertEquals(3, rows.length);
	}
}