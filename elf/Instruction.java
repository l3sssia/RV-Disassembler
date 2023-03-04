package elf;

public class Instruction {
    private int addr;
    private int instr;
    private String name;
    private String arg1;
    private String arg2;
    private String arg3;

    public Instruction(int addr, int instr, String name, String arg1, String arg2, String arg3) {
        this.addr = addr;
        this.instr = instr;
        this.name = name;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.arg3 = arg3;
    }

    public int getAddr() {
        return addr;
    }

    public String toString() {

        String sAdr = Integer.toHexString(addr);
        if (sAdr.length() < 5) {
            sAdr = String.format("%0" + (5 - sAdr.length()) + "d%s", 0, sAdr);
        }
        String sInstr = Integer.toHexString(instr);
        if (sInstr.length() < 8) {
            sInstr = String.format("%0" + (8 - sInstr.length()) + "d%s", 0, sInstr);
        }
        switch (this.name) {
            case "jalr", "lb", "lh", "lw", "lbu", "lhu", "sb", "sh", "sw":
                return String.format("   %s:\t%s\t%7s\t%s, %s(%s)\n", sAdr, sInstr, name, arg1, arg2, arg3);
        }
        if (this.arg3 != null) {
            return String.format("   %s:\t%s\t%7s\t%s, %s, %s\n", sAdr, sInstr, name, arg1, arg2, arg3);
        }
        if (this.arg2 != null) {
            return String.format("   %s:\t%s\t%7s\t%s, %s\n", sAdr, sInstr, name, arg1, arg2);
        }
        return String.format("   %s:\t%s\t%7s\n", sAdr, sInstr, name);
    }
}
