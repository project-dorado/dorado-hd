// Export decompiled C functions and a JSON function index for the current program.
// Run by the pipeline as a Ghidra post-script:
//   analyzeHeadless <proj dir> <proj name> -process <module> -noanalysis \
//       -scriptPath <dir> -postScript ExportDecompiled.java <output root> [maxFunctions]
//
//@category Zune

import ghidra.app.decompiler.DecompileResults;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;
import ghidra.util.task.ConsoleTaskMonitor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class ExportDecompiled extends GhidraScript {

    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        String outRoot = args.length > 0 ? args[0] : ".";
        int maxDecompile = args.length > 1 ? Integer.parseInt(args[1]) : 3000;

        String module = currentProgram.getName().replaceAll("[^A-Za-z0-9._-]", "_");
        File moduleDir = new File(outRoot, module);
        moduleDir.mkdirs();

        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);
        ConsoleTaskMonitor monitor = new ConsoleTaskMonitor();

        StringBuilder index = new StringBuilder("[\n");
        int total = 0;
        int done = 0;
        boolean first = true;

        FunctionIterator functions = currentProgram.getFunctionManager().getFunctions(true);
        while (functions.hasNext()) {
            Function function = functions.next();
            total++;

            String entry = function.getEntryPoint().toString();
            String name = function.getName();
            long size = function.getBody().getNumAddresses();

            if (!first) {
                index.append(",\n");
            }
            first = false;

            index.append(" {")
                 .append("\"name\":\"").append(jsonEsc(name)).append("\",")
                 .append("\"entry\":\"").append(entry).append("\",")
                 .append("\"size\":").append(size).append(",")
                 .append("\"isThunk\":").append(function.isThunk());

            if (done < maxDecompile) {
                DecompileResults result = decomp.decompileFunction(function, 60, monitor);
                if (result != null && result.decompileCompleted()) {
                    String c = result.getDecompiledFunction().getC();
                    if (c != null && c.length() > 0) {
                        File out = new File(moduleDir, entry + "_" + safeName(name) + ".c");
                        try (Writer writer = new OutputStreamWriter(new FileOutputStream(out), "UTF-8")) {
                            writer.write("// " + name + " @ " + entry + "\n");
                            writer.write(c);
                        }
                        index.append(",\"lines\":").append(c.split("\n", -1).length);
                        done++;
                    }
                }
            }

            index.append("}");
        }

        index.append("\n]\n");
        File indexPath = new File(outRoot, module + ".functions.json");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(indexPath), "UTF-8")) {
            writer.write(index.toString());
        }

        println("[ExportDecompiled] " + module + ": " + total + " functions indexed, "
                + done + " decompiled -> " + moduleDir.getAbsolutePath());
    }

    private static String safeName(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            sb.append(Character.isLetterOrDigit(ch) || ch == '.' || ch == '_' || ch == '-'
                    ? ch : '_');
        }
        return sb.toString();
    }

    private static String jsonEsc(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(ch); break;
            }
        }
        return sb.toString();
    }
}
