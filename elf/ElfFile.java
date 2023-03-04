package elf;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

public class ElfFile {
    public static final int EI_MAG_ELF = 0x464c457f;
    public static final byte EI_CLASS_32 = 1;
    public static final byte EI_DATA_LE = 1;
    public static final short E_MACHINE_RISCV = 0xf3;

    public static final int SHT_SYMTAB = 0x02;
    public static final int SHT_STRTAB = 0x03;

    List<Instruction> text = new ArrayList<>();
    SymbolTable symtab = new SymbolTable();
    Labels labels;

    private int textOffset = -1;
    private int symtabOffset = -1;
    private int namesOffset = -1;
    private int strtabOffset = -1;

    private int textSize = -1;
    private int symtabSize = -1;

    private int textAddr = -1;

    private ByteBuffer bytes;
    private BufferedWriter out;
    private int bytesRead;

    private int EI_MAG;
    private byte EI_CLASS;
    private byte EI_DATA;
    private short EI_VERSION;
    private short e_type;
    private short e_machine;
    private int e_version;
    private int e_entry;
    private int e_phoff;
    private int e_shoff;
    private short e_ehsize;
    private short e_phentsize;
    private short e_phnum;
    private short e_shentsize;
    private short e_shnum;
    private short e_shstrndx;

    private int unknownAddr = 0;

    private void parseHeader() {
        if (bytesRead < 54) {
            ElfError("Only " + bytesRead + " bytes in file");
        }

        this.EI_MAG = bytes.getInt(0);
        this.EI_CLASS = bytes.get(0x04);
        this.EI_DATA = bytes.get(0x05);
        this.EI_VERSION = bytes.getShort(0x06);
        this.e_type = bytes.getShort(0x10);
        this.e_machine = bytes.getShort(0x12);
        this.e_version = bytes.getInt(0x14);
        this.e_entry = bytes.getInt(0x18);
        this.e_phoff = bytes.getInt(0x1c);
        this.e_shoff = bytes.getInt(0x20);
        this.e_ehsize = bytes.getShort(0x28);
        this.e_phentsize = bytes.getShort(0x2a);
        this.e_phnum = bytes.getShort(0x2c);
        this.e_shentsize = bytes.getShort(0x2e);
        this.e_shnum = bytes.getShort(0x30);
        this.e_shstrndx = bytes.getShort(0x32);

        if (EI_MAG != EI_MAG_ELF) {
            ElfError("Not elf file");
        }

        if (EI_CLASS != EI_CLASS_32) {
            ElfError("Not 32 bit elf");
        }

        if (EI_DATA != EI_DATA_LE) {
            ElfError("Not little-endian elf");
        }

        if (e_machine != E_MACHINE_RISCV) {
            ElfError("Not RISC-V elf file");
        }

    }

    public ElfFile(String inputName) {
        try {
            byte[] arrayByte = Files.readAllBytes(Paths.get(inputName));
            bytesRead = arrayByte.length;
            bytes = ByteBuffer.wrap(arrayByte);
            bytes.order(ByteOrder.LITTLE_ENDIAN);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Input file not found: " + e.getMessage());
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read from input file: " + e.getMessage());
        }
    }

    public void write(String outputName) {
        try {
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(outputName), "utf8"));
            writeText(writer);
            symtab.write(writer);
            writer.close();

        } catch (IOException e) {
            throw new IllegalArgumentException("Could not open output file: " + e.getMessage());
        }
    }

    public void parse() {
        parseHeader();
        parseSectionHeader();
        parseSymtab();
        labels = symtab.toLabels();
        parseText();
    }

    private String getBytesString(int start, int length) {
        StringBuilder str = new StringBuilder();
        for (int i = start; i < bytesRead && i < start + length; i++) {
            str.append((char) bytes.get(i));
        }
        return str.toString();
    }

    private int getInstruction(int index) {
        return bytes.getInt(index);
    }

    public static String rToString(int d) {
        switch (d) {
            case 0:
                return "zero";
            case 1:
                return "ra";
            case 2:
                return "sp";
            case 3:
                return "gp";
            case 4:
                return "tp";
            case 5, 6, 7:
                return "t" + Integer.toString(d - 5);
            case 8, 9:
                return "s" + Integer.toString(d - 8);
            case 10, 11, 12, 13, 14, 15, 16, 17:
                return "a" + Integer.toString(d - 10);
            case 18, 19, 20, 21, 22, 23, 24, 25, 26, 27:
                return "s" + Integer.toString(d - 16);
            case 28, 29, 30, 31:
                return "t" + Integer.toString(d - 25);
        }
        ElfError("Unknown register x" + d);
        return null;
    }

    private String offsetToString(int addr, int offset) {
        addr += offset;
        if (labels.checkLabel(addr)) {
            return String.format("0x%s <%s>", Integer.toHexString(addr), labels.getLabel(addr));
        }
        String name = String.format("L%d", unknownAddr++);
        labels.add(addr, name);
        return String.format("0x%s <%s>", Integer.toHexString(addr), name);
    }

    public static int getOpcode(int instr) {
        return instr & 0b1111111;
    }

    public int getBits(int instr, int r, int l) {
        return (instr >> l) & ((1 << (r - l + 1)) - 1);
    }

    public static int to12Bits(int x) {
        x = x & (0xfff);
        if ((x & (0x800)) != 0) {
            x = -(x ^ 0xfff) - 1;
        }
        return x;
    }

    public static int to7Bits(int x) {
        x = x & (0b1111111);
        if ((x & (0b1000000)) != 0) {
            x = -(x ^ 0b1111111) - 1;
        }
        return x;
    }

    public static int to20Bits(int x) {
        x = x & (0xfffff);
        if ((x & (0x80000)) != 0) {
            x = -(x ^ 0xfffff) - 1;
        }
        return x;
    }

    private void parseSectionHeader() {
        for (int i = e_shoff; i + 0x24 < bytesRead; i += 0x28) {
            int index = (i - e_shoff) / 0x28;
            if (index == e_shstrndx && bytes.getInt(i + 0x04) == SHT_STRTAB) {
                namesOffset = bytes.getInt(i + 0x10);
                break;
            }
        }
        if (namesOffset == -1) {
            ElfError("Section names not found");
        }
        for (int i = e_shoff; i + 0x24 < bytesRead; i += 0x28) {
            // .text -- 5 bytes
            // .symtab -- 7 bytes
            int sh_name = bytes.getInt(i);
            String textName = getBytesString(namesOffset + sh_name, 5);
            if (textName.equals(".text")) {
                textAddr = bytes.getInt(i + 0x0c);
                textOffset = bytes.getInt(i + 0x10);
                textSize = bytes.getInt(i + 0x14);
            }
            String symtabName = getBytesString(namesOffset + sh_name, 7);
            if (symtabName.equals(".symtab") &&
                    bytes.getInt(i + 0x04) == SHT_SYMTAB) {
                symtabOffset = bytes.getInt(i + 0x10);
                symtabSize = bytes.getInt(i + 0x14);
            }

            String strtabName = getBytesString(namesOffset + sh_name, 7);
            if (symtabName.equals(".strtab") &&
                    bytes.getInt(i + 0x04) == SHT_STRTAB) {
                strtabOffset = bytes.getInt(i + 0x10);
            }

        }
        if (symtabOffset == -1) {
            ElfError("Section .symtab not found");
        }
        if (symtabOffset == -1) {
            ElfError("Section .text not found");
        }
        if (strtabOffset == -1) {
            ElfError("Section .strtab not found");
        }
    }

    private void parseText() {
        for (int i = 0; i < textSize && textOffset + i < bytesRead; i += 4) {
            int x = getInstruction(textOffset + i);
            int addr = textAddr + i;
            int opcode = getOpcode(x);
            int func3 = getBits(x, 14, 12);
            int func7 = getBits(x, 31, 25);
            int offset;
            String name = null;
            String arg1 = null;
            String arg2 = null;
            String arg3 = null;
            switch (opcode) {
                case (0b0110111):
                    name = "lui";
                    arg1 = rToString(getBits(x, 11, 7));
                    arg2 = Integer.toString(getBits(x, 31, 12));
                    break;
                case (0b0010111):
                    name = "auipc";
                    arg1 = rToString(getBits(x, 11, 7));
                    arg2 = Integer.toString(getBits(x, 31, 12));
                    break;
                case (0b0010011):
                    arg1 = rToString(getBits(x, 11, 7));
                    arg2 = rToString(getBits(x, 19, 15));
                    switch (func3) {
                        case (0b000):
                            arg3 = Integer.toString(to12Bits(getBits(x, 31, 20)));
                            name = "addi";
                            break;
                        case (0b010):
                            arg3 = Integer.toString(to12Bits(getBits(x, 31, 20)));
                            name = "slti";
                            break;
                        case (0b011):
                            arg3 = Integer.toString(getBits(x, 31, 20));
                            name = "sltiu";
                            break;
                        case (0b100):
                            arg3 = Integer.toString(to12Bits(getBits(x, 31, 20)));
                            name = "xori";
                            break;
                        case (0b110):
                            arg3 = Integer.toString(to12Bits(getBits(x, 31, 20)));
                            name = "ori";
                            break;
                        case (0b111):
                            arg3 = Integer.toString(to12Bits(getBits(x, 31, 20)));
                            name = "andi";
                            break;
                        case (0b001):
                            arg3 = Integer.toString(getBits(x, 24, 20));
                            name = "slli";
                            break;
                        case (0b101):
                            switch (func7 | 1) {
                                case (0b0000001):
                                    arg3 = Integer.toString(getBits(x, 24, 20));
                                    name = "srli";
                                    break;
                                case (0b0100001):
                                    arg3 = Integer.toString(getBits(x, 24, 20));
                                    name = "srai";
                                    break;
                                default:
                                    name = "unknown_instruction";
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                            }
                            break;
                        default:
                            name = "unknown_instruction";
                            arg1 = null;
                            arg2 = null;
                            arg3 = null;

                    }
                    break;

                case (0b0110011):
                    arg1 = rToString(getBits(x, 11, 7));
                    arg2 = rToString(getBits(x, 19, 15));
                    arg3 = rToString(getBits(x, 24, 20));
                    switch (func3) {
                        case (0b000):
                            switch (func7) {
                                case (0):
                                    name = "add";
                                    break;
                                case (0b0100000):
                                    name = "sub";
                                    break;
                                case (0b0000001):
                                    name = "mul";
                                    break;
                                default:
                                    name = "unknown_instruction";
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                            }
                            break;
                        case (0b001):
                            switch (func7) {
                                case (0):
                                    name = "sll";
                                    break;
                                case (0b0000001):
                                    name = "mulh";
                                    break;
                                default:
                                    name = "unknown_instruction";
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                            }
                            break;
                        case (0b010):
                            switch (func7) {
                                case (0):
                                    name = "slt";
                                    break;
                                case (0b0000001):
                                    name = "mulhsu";
                                    break;
                                default:
                                    name = "unknown_instruction";
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                            }
                            break;
                        case (0b011):
                            switch (func7) {
                                case (0):
                                    name = "sltu";
                                    break;
                                case (0b0000001):
                                    name = "mulhu";
                                    break;
                                default:
                                    name = "unknown_instruction";
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                            }
                            break;
                        case (0b100):
                            switch (func7) {
                                case (0):
                                    name = "xor";
                                    break;
                                case (0b0000001):
                                    name = "div";
                                    break;
                                default:
                                    name = "unknown_instruction";
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                            }
                            break;
                        case (0b101):
                            switch (func7) {
                                case (0):
                                    name = "srl";
                                    break;
                                case (0b0100000):
                                    name = "sra";
                                    break;
                                case (0b0000001):
                                    name = "divu";
                                    break;
                                default:
                                    name = "unknown_instruction";
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                            }
                            break;
                        case (0b110):
                            switch (func7) {
                                case (0):
                                    name = "or";
                                    break;
                                case (0b0000001):
                                    name = "rem";
                                    break;
                                default:
                                    name = "unknown_instruction";
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                            }
                            break;
                        case (0b111):
                            switch (func7) {
                                case (0):
                                    name = "and";
                                    break;
                                case (0b0000001):
                                    name = "remu";
                                    break;
                                default:
                                    name = "unknown_instruction";
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                            }
                            break;
                        default:
                            name = "unknown_instruction";
                            arg1 = null;
                            arg2 = null;
                            arg3 = null;
                    }
                    break;
                case (0b0001111):
                    name = "fence";
                    arg1 = null;
                    arg2 = null;
                    arg3 = null;
                    break;
                case (0b1110011):
                    arg1 = rToString(getBits(x, 11, 7));
                    switch (func3) {
                        case (0b000):
                            switch (getBits(x, 31, 7)) {
                                case (0):
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                                    name = "ecall";
                                    break;
                                case (0b10000000000000):
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                                    name = "ebreak";
                                    break;
                                case (0b100000000000000):
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                                    name = "uret";
                                    break;
                                case (0b0001000000100000000000000):
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                                    name = "sret";
                                    break;
                                case (0b0011000000100000000000000):
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                                    name = "mret";
                                    break;
                                case (0b0001000001010000000000000):
                                    arg1 = null;
                                    arg2 = null;
                                    arg3 = null;
                                    name = "wfi";
                                    break;
                                default:
                                    if (func7 == 0b0001001) {
                                        arg1 = null;
                                        arg2 = null;
                                        arg3 = null;
                                        name = "sfence.vma";
                                    } else {
                                        name = "unknown_instruction";
                                        arg1 = null;
                                        arg2 = null;
                                        arg3 = null;
                                    }
                            }
                            break;
                        default:
                            name = "unknown_instruction";
                            arg1 = null;
                            arg2 = null;
                            arg3 = null;
                    }
                    break;

                case (0b0000011):
                    arg1 = rToString(getBits(x, 11, 7));
                    arg2 = Integer.toString(getBits(x, 31, 20));
                    arg3 = rToString(getBits(x, 19, 15));
                    switch (func3) {
                        case 0b000:
                            name = "lb";
                            break;
                        case 0b001:
                            name = "lh";
                            break;
                        case 0b010:
                            name = "lw";
                            break;
                        case 0b100:
                            name = "lbu";
                            break;
                        case 0b101:
                            name = "lhu";
                            break;

                        default:
                            name = "unknown_instruction";
                            arg1 = null;
                            arg2 = null;
                            arg3 = null;
                    }
                    break;

                case (0b0100011):
                    offset = getBits(x, 11, 7) | (getBits(x, 31, 25) << 5);
                    arg1 = rToString(getBits(x, 24, 20));
                    arg2 = Integer.toString(offset);
                    arg3 = rToString(getBits(x, 19, 15));
                    switch (func3) {
                        case 0b000:
                            name = "sb";
                            break;
                        case 0b001:
                            name = "sh";
                            break;
                        case 0b010:
                            name = "sw";
                            break;

                        default:
                            name = "unknown_instruction";
                            arg1 = null;
                            arg2 = null;
                            arg3 = null;
                    }
                    break;

                case (0b1101111):
                    offset = (getBits(x, 31, 31) << 20) | (getBits(x, 30, 21) << 1)
                            | (getBits(x, 20, 20) << 11) | (getBits(x, 19, 12) << 12);
                    arg1 = rToString(getBits(x, 11, 7));
                    arg2 = offsetToString(addr, to20Bits(offset));
                    name = "jal";
                    break;
                case (0b1100111):
                    if (func3 != 0) {
                        name = "unknown_instruction";
                        arg1 = null;
                        arg2 = null;
                        arg3 = null;
                        break;
                    }
                    arg1 = null;
                    arg2 = null;
                    arg3 = null;
                    arg1 = rToString(getBits(x, 11, 7));
                    arg2 = Integer.toString(getBits(x, 31, 20));
                    arg3 = rToString(getBits(x, 19, 15));
                    name = "jalr";
                    break;
                case (0b1100011):
                    offset = (getBits(x, 31, 31) << 12) | (getBits(x, 30, 25) << 5) |
                            (getBits(x, 11, 8) << 1) | (getBits(x, 7, 7) << 11);
                    arg1 = rToString(getBits(x, 19, 15));
                    arg2 = rToString(getBits(x, 24, 20));
                    arg3 = offsetToString(addr, to12Bits(offset));
                    switch (func3) {
                        case 0b000:
                            name = "beq";
                            break;
                        case 0b001:
                            name = "bne";
                            break;
                        case 0b100:
                            name = "blt";
                            break;
                        case 0b101:
                            name = "bge";
                            break;
                        case 0b110:
                            name = "bltu";
                            break;
                        case 0b111:
                            name = "bgeu";
                            break;
                        default:
                            name = "unknown_instruction";
                            arg1 = null;
                            arg2 = null;
                            arg3 = null;
                    }
                    break;

                default:
                    name = "unknown_instruction";
                    arg1 = null;
                    arg2 = null;
                    arg3 = null;
            }
            text.add(new Instruction(addr, x, name, arg1, arg2, arg3));
        }
    }

    private void parseSymtab() {
        for (int i = symtabOffset, symbol = 0; i < symtabOffset + symtabSize; i += 0x10, symbol++) {
            int value = bytes.getInt(i + 4);
            int size = bytes.getInt(i + 8);
            int type = bytes.get(i + 12) % 0x10;
            int bind = bytes.get(i + 12) / 0x10;
            int vis = bytes.get(i + 13);
            short index = bytes.getShort(i + 14);
            String name = parseSymbolName(strtabOffset + bytes.getInt(i));
            symtab.add(new SymbolTable.Symbol(symbol, value, size, type, bind, vis, index, name));
        }
    }

    private String parseSymbolName(int index) {
        StringBuilder str = new StringBuilder();
        for (int i = index; i < bytesRead && bytes.get(i) != 0; i++) {
            str.append((char) bytes.get(i));
        }
        return str.toString();
    }

    private void writeText(BufferedWriter writer) throws IOException {
        writer.write(".text\n");
        for (Instruction i : text) {
            if (labels.checkLabel(i.getAddr())) {
                String addr = Integer.toHexString(i.getAddr());
                if (addr.length() < 8) {
                    addr = String.format("%0" + (8 - addr.length()) + "d%s", 0, addr);
                }
                String label = labels.getLabel(i.getAddr());
                writer.write(String.format("%s   <%s>:\n", addr, label));
            }
            writer.write(i.toString());
        }
    }

    public static void ElfError(String msg) {
        throw new IllegalStateException(msg);
    }
}
