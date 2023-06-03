# RISC-V Disassembler
## Modules: RV32I, RV32M
## Language: Java (OpenJDK 17.0.4.1)
The classes for disassembling are located in the elf package, and the main class is named RVDisassembler, which takes two arguments - the name of the input file and the name of the output file.

Result contains assembly code of following sections: `.text`, `.symtab`.

### Example:
Input file (ELF): [test_elf](RV-Disassembler/test_elf)

Output file (Assembly code): [out.txt](RV-Disassembler/out.txt)

Fragment of disassembly of section `.text`:
`
00010074   <register_fini>:
   10074:	00000793	   addi	a5, zero, 0
   10078:	00078863	    beq	a5, zero, 0x10088 <L0>
   1007c:	00010537	    lui	a0, 16
   10080:	48c50513	   addi	a0, a0, 1164
   10084:	3f40006f	    jal	zero, 0x10478 <atexit>
`
Fragment of disassembly of section `.symtab`:
`
Symbol Value              Size Type     Bind     Vis       Index Name
[   0] 0x0                   0 NOTYPE   LOCAL    DEFAULT     UND 
[   1] 0x10074               0 SECTION  LOCAL    DEFAULT       1 
[   2] 0x115cc               0 SECTION  LOCAL    DEFAULT       2 
[   3] 0x115d0               0 SECTION  LOCAL    DEFAULT       3 
[   4] 0x115d8               0 SECTION  LOCAL    DEFAULT       4 
[   5] 0x115e0               0 SECTION  LOCAL    DEFAULT       5 
[   6] 0x11a08               0 SECTION  LOCAL    DEFAULT       6 
[   7] 0x11a14               0 SECTION  LOCAL    DEFAULT       7 
[   8] 0x0                   0 SECTION  LOCAL    DEFAULT       8 
[   9] 0x0                   0 SECTION  LOCAL    DEFAULT       9 
[  10] 0x0                   0 FILE     LOCAL    DEFAULT     ABS __call_atexit.c
[  11] 0x10074              24 FUNC     LOCAL    DEFAULT       1 register_fini
[  12] 0x0                   0 FILE     LOCAL    DEFAULT     ABS crtstuff.c
`
