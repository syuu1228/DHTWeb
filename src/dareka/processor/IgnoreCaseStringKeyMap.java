package dareka.processor;

import java.util.Comparator;
import java.util.TreeMap;

public class IgnoreCaseStringKeyMap<E> extends TreeMap<String, E> {
    private static final long serialVersionUID = 4337680567412157638L;

    public IgnoreCaseStringKeyMap() {
        super(new Comparator<String>() {
            public int compare(String o1, String o2) {
                return o1.compareToIgnoreCase(o2);
            }
        });
    }

    /**
     * TreeMap implements Cloneable.
     */
    @Override
    public Object clone() {
        return super.clone();
    }
}
