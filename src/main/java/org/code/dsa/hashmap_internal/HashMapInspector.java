package org.code.dsa.hashmap_internal;

import java.lang.reflect.Field;
import java.util.HashMap;



public class HashMapInspector {



    static final class FixedHashKey implements Comparable<FixedHashKey> {
        final int id;
        final int hash;

        FixedHashKey(int id, int hash) {
            this.id = id;
            this.hash = hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof FixedHashKey other && other.id == id;
        }

        @Override
        public int compareTo(FixedHashKey o) {
            return Integer.compare(id, o.id);
        }

        @Override
        public String toString() {
            return "K" + id ;
        }
    }


    private static final Field TABLE, THRESHOLD, LOAD_FACTOR, MOD_COUNT, SIZE;
    private static final Field NODE_HASH, NODE_KEY, NODE_VALUE, NODE_NEXT;
    private static final Class<?> TREE_NODE_CLASS;

    static {
        try {
            TABLE = open(HashMap.class, "table");
            THRESHOLD = open(HashMap.class, "threshold");
            LOAD_FACTOR = open(HashMap.class, "loadFactor");
            MOD_COUNT = open(HashMap.class, "modCount");
            SIZE = open(HashMap.class, "size");

            Class<?> nodeClass = Class.forName("java.util.HashMap$Node");
            NODE_HASH = open(nodeClass, "hash");
            NODE_KEY = open(nodeClass, "key");
            NODE_VALUE = open(nodeClass, "value");
            NODE_NEXT = open(nodeClass, "next");
            TREE_NODE_CLASS = Class.forName("java.util.HashMap$TreeNode");
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException(
                    "Cannot access HashMap internals. Run with: "
                            + "--add-opens java.base/java.util=ALL-UNNAMED", e);
        }
    }

    private static Field open(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    // Same "spread" function HashMap uses: XOR high 16 bits into low 16 bits
    static int spread(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    static String bits(int value) {
        String s = String.format("%32s", Integer.toBinaryString(value)).replace(' ', '0');
        return s.substring(0, 16) + "_" + s.substring(16);
    }

    // ---------------------------------------------------------------------
    // Show step by step how a key's bucket index is calculated
    // ---------------------------------------------------------------------
    static void explainIndex(Object key, int capacity) {
        int h = key.hashCode();
        int spread = spread(key);
        int index = (capacity - 1) & spread;
        System.out.println("  Key \"" + key + "\" with capacity " + capacity);
        System.out.println("    hashCode()          = " + h);
        System.out.println("    hashCode bits       = " + bits(h));
        System.out.println("    h >>> 16            = " + bits(h >>> 16));
        System.out.println("    spread = h ^ (h>>>16) " + bits(spread) + "  (" + spread + ")");
        System.out.println("    capacity - 1        = " + bits(capacity - 1));
        System.out.println("    index = (n-1) & hash = " + index);
    }

    // ---------------------------------------------------------------------
    // Print the whole internal state of a HashMap
    // ---------------------------------------------------------------------
    static void inspect(String title, HashMap<?, ?> map) throws IllegalAccessException {
        Object[] table = (Object[]) TABLE.get(map);
        int capacity = (table == null) ? 0 : table.length;

        System.out.println("\n=== " + title + " ===");
        System.out.printf("  size=%d  capacity=%d  threshold=%d  loadFactor=%.2f  modCount=%d%n",
                SIZE.getInt(map), capacity, THRESHOLD.getInt(map),
                LOAD_FACTOR.getFloat(map), MOD_COUNT.getInt(map));

        if (table == null) {
            System.out.println("  table is null (HashMap allocates the array lazily on the first put)");
            return;
        }

        int usedBuckets = 0;
        for (int i = 0; i < table.length; i++) {
            Object node = table[i];
            if (node == null) continue;
            usedBuckets++;

            String kind = TREE_NODE_CLASS.isInstance(node) ? "TREE" : "LIST";
            StringBuilder chain = new StringBuilder();
            int length = 0;
            // TreeNode also keeps the "next" links, so this walks both kinds
            for (Object n = node; n != null; n = NODE_NEXT.get(n)) {
                if (length > 0) chain.append(" -> ");
                chain.append(NODE_KEY.get(n)).append('=').append(NODE_VALUE.get(n))
                        .append(" [hash=").append(NODE_HASH.getInt(n)).append(']');
                length++;
            }
            System.out.printf("  bucket %2d  %s  length=%d  %s%n", i, kind, length, chain);
        }
        System.out.printf("  buckets used: %d of %d%n", usedBuckets, capacity);
    }

    // ---------------------------------------------------------------------
    public static void main(String[] args) throws Exception {

        // 1. Lazy allocation
        HashMap<String, Integer> map = new HashMap<>();
        inspect("1. Empty map right after new HashMap<>()", map);

        // 2. Normal inserts: default capacity 16, load factor 0.75, threshold 12
        String[] fruits = {"apple", "banana", "cherry", "date", "elderberry", "fig"};
        for (int i = 0; i < fruits.length; i++) map.put(fruits[i], i);
        inspect("2. After 6 puts", map);

        System.out.println("\n--- How \"apple\" got its bucket ---");
        explainIndex("apple", 16);

        // 3. Collision: "Aa" and "BB" have the same hashCode (2112)
        map.put("Aa", 100);
        map.put("BB", 200);
        System.out.println("\n\"Aa\".hashCode() = " + "Aa".hashCode()
                + ", \"BB\".hashCode() = " + "BB".hashCode());
        inspect("3. After putting colliding keys \"Aa\" and \"BB\"", map);

        // 4. Resize: crossing threshold 12 doubles capacity to 32
        for (int i = 0; i < 6; i++) map.put("key" + i, i);
        inspect("4. After crossing the threshold (resize 16 -> 32)", map);

        // 5. Null key always goes to bucket 0
        map.put(null, -1);
        inspect("5. After put(null, -1)", map);

        // 6. Treeification: many keys with the SAME hash in one bucket.
        //    A bucket turns into a red-black tree when it has more than 8 nodes
        //    AND the table has at least 64 buckets; below 64, HashMap resizes instead.
        HashMap<FixedHashKey, String> collisions = new HashMap<>();
        for (int i = 1; i <= 12; i++) {
            collisions.put(new FixedHashKey(i, 7), "v" + i);
            Object[] t = (Object[]) TABLE.get(collisions);
            int idx = (t.length - 1) & spread(new FixedHashKey(0, 7));
            String kind = TREE_NODE_CLASS.isInstance(t[idx]) ? "TREE" : "LIST";
            System.out.printf("  put #%2d -> capacity=%3d  bucket %d is %s%n", i, t.length, idx, kind);
        }
        inspect("6. All 12 keys share hash 7 (treeified bucket)", collisions);

        // 7. Custom initial capacity: HashMap rounds up to the next power of two
        HashMap<String, String> sized = new HashMap<>(20, 0.5f);
        sized.put("x", "y");
        inspect("7. new HashMap<>(20, 0.5f) after one put (capacity rounds up to 32)", sized);

        // 8. modCount changes on structural modification -> fail-fast iterators
        int before = MOD_COUNT.getInt(map);
        map.put("apple", 999);                  // replacing a value: NOT structural
        int afterReplace = MOD_COUNT.getInt(map);
        map.remove("fig");                      // removal: structural
        int afterRemove = MOD_COUNT.getInt(map);
        System.out.printf("%n8. modCount: before=%d, after replacing a value=%d, after remove=%d%n",
                before, afterReplace, afterRemove);
    }

}
