package elf;

import java.util.Map;
import java.util.TreeMap;

public class Labels {
    private Map<Integer, String> labels = new TreeMap<>();

    public void add(int adr, String name) {
        labels.put(adr, name);
    }

    public boolean checkLabel(int adr) {
        return labels.containsKey(adr);
    }

    public String getLabel(int adr) {
        return labels.get(adr);
    }
}
