package me.yaotouwan.post;

import android.database.*;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jason on 14-4-14.
 */
public class PostCursor extends AbstractCursor {

    private final String[] columnNames;
    private List<Object[]> data;

    public PostCursor(String[] columnNames, int initialCapacity) {
        this.columnNames = columnNames;
        this.data = new ArrayList<Object[]>(initialCapacity);
    }

    public PostCursor(String[] columnNames) {
        this(columnNames, 16);
    }

    private Object get(int column) {
        return data.get(mPos)[column];
    }

    public void addRow(Object... columnValues) {
        data.add(columnValues);
    }

    public void insertRow(Object... columnValues) {
        data.add(0, columnValues);
    }

    public void updateRow(int pos, Object... columnValues) {
        data.set(pos, columnValues);
    }

    public void updateRowAtColumn(int row, int column, Object value) {
        data.get(row)[column] = value;
    }

    public void removeRow(int pos) {
        data.remove(pos);
    }

    // AbstractCursor implementation.

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public String[] getColumnNames() {
        return columnNames;
    }

    @Override
    public String getString(int column) {
        Object value = get(column);
        if (value == null) return null;
        return value.toString();
    }

    @Override
    public short getShort(int column) {
        Object value = get(column);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).shortValue();
        return Short.parseShort(value.toString());
    }

    @Override
    public int getInt(int column) {
        Object value = get(column);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    @Override
    public long getLong(int column) {
        Object value = get(column);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    @Override
    public float getFloat(int column) {
        Object value = get(column);
        if (value == null) return 0.0f;
        if (value instanceof Number) return ((Number) value).floatValue();
        return Float.parseFloat(value.toString());
    }

    @Override
    public double getDouble(int column) {
        Object value = get(column);
        if (value == null) return 0.0d;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    @Override
    public byte[] getBlob(int column) {
        Object value = get(column);
        return (byte[]) value;
    }

    @Override
    public int getType(int column) {
        Object obj = get(column);
        if (obj == null) {
            return Cursor.FIELD_TYPE_NULL;
        } else if (obj instanceof byte[]) {
            return Cursor.FIELD_TYPE_BLOB;
        } else if (obj instanceof Float || obj instanceof Double) {
            return Cursor.FIELD_TYPE_FLOAT;
        } else if (obj instanceof Long || obj instanceof Integer
                || obj instanceof Short || obj instanceof Byte) {
            return Cursor.FIELD_TYPE_INTEGER;
        } else {
            return Cursor.FIELD_TYPE_STRING;
        }
    }

    public Object getValue(int row, int column) {
        return data.get(row)[column];
    }

    public boolean isNull(int row, int column) {
        return data.get(row)[column] == null;
    }

    @Override
    public boolean isNull(int column) {
        return get(column) == null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (Object[] objects : data) {
            sb.append("{");
            StringBuilder rsb = new StringBuilder();
            for (int i=0; i<objects.length; i++) {
                Object obj = objects[i];
                rsb.append(obj!=null?obj.toString():"<null>");
                rsb.append(", ");
            }
            sb.append(rsb);
            sb.append("},\n");
        }
        sb.append("]");
        return sb.toString();
    }

    public void move(int pos1, int pos2) {
        Object[] obj1 = data.get(pos1);
        if (pos1 < pos2) {
            for (int i=pos1; i<pos2; i++) {
                data.set(i, data.get(i+1));
            }
        } else {
            for (int i=pos1; i>pos2; i--) {
                data.set(i, data.get(i-1));
            }
        }
        data.set(pos2, obj1);
    }
}
