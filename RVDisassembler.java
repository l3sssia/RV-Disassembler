import elf.ElfFile;

public class RVDisassembler {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Enter 2 arguments: input file name (elf) and output file name");
        }
        ElfFile elf = new ElfFile(args[0]);
        elf.parse();
        elf.write(args[1]);
    }
}
