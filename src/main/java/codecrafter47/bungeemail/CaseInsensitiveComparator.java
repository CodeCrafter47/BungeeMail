package codecrafter47.bungeemail;

import java.util.Comparator;

class CaseInsensitiveComparator implements Comparator<String> {

    public final static CaseInsensitiveComparator INSTANCE = new CaseInsensitiveComparator();

    @Override
    public int compare(String o1, String o2) {
        return o1.toLowerCase().compareTo(o2.toLowerCase());
    }
}
