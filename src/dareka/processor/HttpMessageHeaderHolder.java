package dareka.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HttpMessageHeaderHolder {
    private IgnoreCaseStringKeyMap<List<String>> map = new IgnoreCaseStringKeyMap<List<String>>();

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();

            for (String value : values) {
                result.append(key);
                result.append(": ");
                result.append(value);
                result.append("\r\n");
            }
        }

        return result.toString();
    }

    public String get(String key) {
        List<String> values = map.get(key);
        if (values == null) {
            return null;
        } else {
            return values.get(values.size() - 1);
        }
    }

    public void put(String key, String value) {
        List<String> values = newList(value);
        map.put(key, values);
    }

    public void add(String key, String value) {
        List<String> values = map.get(key);
        if (values == null) {
            values = newList(value);
            map.put(key, values);
        } else {
            values.add(value);
        }
    }

    public void remove(String key) {
        map.remove(key);
    }
    
    public Set<Map.Entry<String, List<String>>> entrySet() {
        return map.entrySet();
    }

    /**
     * Create new List for values. This method is for (1) localize the
     * dependency to the implimentation class, and (2) ensure that the values
     * List has at least one element.
     * 
     * @param initialElement
     * @return List instance for values.
     */
    private List<String> newList(String initialElement) {
        List<String> result = new ArrayList<String>();
        result.add(initialElement);
        return result;
    }
}
