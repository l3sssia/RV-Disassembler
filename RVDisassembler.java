import elf.ElfFile;

public class RVDisassembler {
    public static void main(String[] args) {
        ElfFile elf = new ElfFile(args[0]);
        elf.parse();
        elf.write(args[1]);
    }
}
