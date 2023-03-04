package elf;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.*;
import java.util.*;

public class SymbolTable {

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;
    public static final int STB_LOOS = 10;
    public static final int STB_HIOS = 12;
    public static final int STB_LOPROC = 13;
    public static final int STB_HIPROC = 15;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;
    public static final int STV_EXPORTED = 4;
    public static final int STV_SINGLETON = 5;
    public static final int STV_ELIMINATE = 6;

    public static final short SHN_UNDEF = 0;
    public static final short SHN_LORESERVE = (short) 0xff00;
    public static final short SHN_LOPROC = (short) 0xff00;
    public static final short SHN_HIPROC = (short) 0xff1f;
    public static final short SHN_ABS = (short) 0xfff1;
    public static final short SHN_COMMON = (short) 0xfff2;
    public static final short SHN_XINDEX = (short) 0xffff;
    public static final short SHN_HIRESERVE = (short) 0xffff;
    public static final short SHN_LOOS = (short) 0xff20;
    public static final short SHN_HIOS = (short) 0xff3f;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 3;
    public static final int STT_FILE = 4;
    public static final int STT_COMMON = 5;
    public static final int STT_TLS = 6;
    public static final int STT_LOOS = 10;
    public static final int STT_HIOS = 12;
    public static final int STT_LOPROC = 13;
    public static final int STT_HIPROC = 15;

    static class Symbol extends SymbolTable {
        private int symbol;
        private int value;
        private int size;
        private int type;
        private int bind;
        private int vis;
        private short index;
        private String name;

        public Symbol(int symbol, int value, int size, int type, int bind, int vis, short index, String name) {
            this.symbol = symbol;
            this.value = value;
            this.size = size;
            this.type = type;
            this.bind = bind;
            this.vis = vis;
            this.index = index;
            this.name = name;
        }

        public String typeToString() {
            switch (this.type) {
                case (STT_NOTYPE):
                    return "NOTYPE";
                case (STT_OBJECT):
                    return "OBJECT";
                case (STT_FUNC):
                    return "FUNC";
                case (STT_SECTION):
                    return "SECTION";
                case (STT_FILE):
                    return "FILE";
                case (STT_COMMON):
                    return "COMMON";
                case (STT_TLS):
                    return "TLS";
                case (STT_LOOS):
                    return "LOOS";
                case (STT_HIOS):
                    return "HIOS";
                case (STT_LOPROC):
                    return "LOPROC";
                case (STT_HIPROC):
                    return "HIPROC";
                default:
                    return "UNKNOWN";
            }
        }

        public String bindToString() {
            switch (this.bind) {
                case (STB_LOCAL):
                    return "LOCAL";
                case (STB_GLOBAL):
                    return "GLOBAL";
                case (STB_WEAK):
                    return "WEAK";
                case (STB_LOOS):
                    return "LOOS";
                case (STB_HIOS):
                    return "HIOS";
                case (STB_LOPROC):
                    return "LOPROC";
                case (STB_HIPROC):
                    return "HIPROC";
                default:
                    return "UNKNOWN";
            }
        }

        public String visToString() {
            switch (this.vis) {
                case STV_HIDDEN:
                    return "HIDDEN";
                case STV_DEFAULT:
                    return "DEFAULT";
                case STV_INTERNAL:
                    return "INTERNAL";
                case STV_PROTECTED:
                    return "PROTECTED";
                case STV_EXPORTED:
                    return "EXPORTED";
                case STV_SINGLETON:
                    return "SINGLETON";
                case STV_ELIMINATE:
                    return "ELIMINATE";
                default:
                    return "UNKNOWN";
            }
        }

        public String indexToString() {
            switch (this.index) {
                case SHN_UNDEF:
                    return "UNDEF";
                case SHN_LOPROC:
                    return "LOPROC";
                case SHN_HIPROC:
                    return "HIPROC";
                case SHN_ABS:
                    return "ABS";
                case SHN_COMMON:
                    return "COMMON";
                case SHN_XINDEX:
                    return "XINDEX";
                case SHN_LOOS:
                    return "LOOS";
                case SHN_HIOS:
                    return "HIOS";
                default:
                    return Integer.toString((int) index & (0xffff));
            }
        }

        public String toString() {
            return String.format("[%4d] 0x%-15x %5d %-8s %-8s %-8s %6s %s\n", symbol, value, size,
                    typeToString(), bindToString(), visToString(), indexToString(), name);
        }

    }

    private List<Symbol> symtab = new ArrayList<Symbol>();

    public void add(Symbol e) {
        symtab.add(e);
    }

    public void write(BufferedWriter writer) throws IOException {
        writer.write("\n.symtab\n");
        writer.write(String.format("%s %-15s %7s %-8s %-8s %-8s %6s %s\n",
                "Symbol", "Value", "Size", "Type",
                "Bind", "Vis", "Index", "Name"));
        for (Symbol i : symtab) {
            writer.write(i.toString());
        }
    }

    public Labels toLabels() {
        Labels labels = new Labels();
        for (Symbol symbol : symtab) {
            if (symbol.type == STT_FUNC) {
                labels.add(symbol.value, symbol.name);
            }
        }
        return labels;
    }
}
