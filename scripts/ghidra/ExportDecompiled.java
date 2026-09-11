// Export decompiled C functions, a JSON function index, and a string index for the
// current program. Optionally applies recovered export/import symbol names first.
//
// Run by the pipeline as a Ghidra post-script:
//   analyzeHeadless <proj dir> <proj name> -import <module> \
//       -scriptPath <dir> \
//       -postScript ExportDecompiled.java <outputRoot> <maxFunctions|0=all> [symbolsJson]
//
//@category Zune

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ghidra.app.decompiler.DecompileResults;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.StringDataInstance;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;
import ghidra.program.model.symbol.SourceType;
import ghidra.util.task.ConsoleTaskMonitor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

public class ExportDecompiled extends GhidraScript {

    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        String outRoot = args.length > 0 ? args[0] : ".";
        int maxDecompile = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        String symbolsPath = args.length > 2 ? args[2] : null;

        String module = currentProgram.getName().replaceAll("[^A-Za-z0-9._-]", "_");
        File moduleDir = new File(outRoot, module);
        moduleDir.mkdirs();

        // ---- apply recovered export/import names (best effort) -----------------
        int renamed = 0;
        Set<Long> exportAddrs = new HashSet<Long>();
        if (symbolsPath != null && new File(symbolsPath).isFile()) {
            renamed = applySymbols(symbolsPath, exportAddrs);
            println("[ExportDecompiled] applied " + renamed + " symbols from " + symbolsPath);
        }

        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);
        ConsoleTaskMonitor monitor = new ConsoleTaskMonitor();

        JsonArray index = new JsonArray();
        int total = 0;
        int done = 0;

        FunctionIterator functions = currentProgram.getFunctionManager().getFunctions(true);
        while (functions.hasNext()) {
            Function function = functions.next();
            total++;

            Address entryAddr = function.getEntryPoint();
            String entry = entryAddr.toString();
            String name = function.getName();
            long size = function.getBody().getNumAddresses();

            JsonObject rec = new JsonObject();
            rec.addProperty("name", name);
            rec.addProperty("entry", entry);
            rec.addProperty("size", size);
            rec.addProperty("isThunk", function.isThunk());
            rec.addProperty("isExport", exportAddrs.contains(entryAddr.getUnsignedOffset()));
            try {
                rec.addProperty("signature", function.getPrototypeString(false, false));
            } catch (Exception ignore) {
                // signature not always available
            }

            boolean shouldDecompile = (maxDecompile <= 0) || (done < maxDecompile);
            if (shouldDecompile) {
                DecompileResults result = decomp.decompileFunction(function, 60, monitor);
                if (result != null && result.decompileCompleted()) {
                    String c = result.getDecompiledFunction().getC();
                    if (c != null && c.length() > 0) {
                        File out = new File(moduleDir, entry + "_" + safeName(name) + ".c");
                        try (Writer writer = new OutputStreamWriter(new FileOutputStream(out), "UTF-8")) {
                            writer.write("// " + name + " @ " + entry + "\n");
                            writer.write(c);
                        }
                        rec.addProperty("lines", c.split("\n", -1).length);
                        done++;
                    }
                }
            }
            index.add(rec);
        }

        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(new File(outRoot, module + ".functions.json")), "UTF-8")) {
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(index));
        }

        // ---- string index ------------------------------------------------------
        JsonArray strings = new JsonArray();
        for (Data data : currentProgram.getListing().getDefinedData(true)) {
            if (data == null) {
                continue;
            }
            Object value = null;
            try {
                value = data.getValue();
            } catch (Exception ignore) {
                continue;
            }
            if (!(value instanceof String)) {
                continue;
            }
            String text = (String) value;
            if (text == null || text.isEmpty() || text.length() > 4096) {
                continue;
            }
            JsonObject s = new JsonObject();
            s.addProperty("address", data.getAddress().toString());
            s.addProperty("value", text);
            Function owner = getFunctionContaining(data.getAddress());
            if (owner != null) {
                s.addProperty("function", owner.getName());
                s.addProperty("functionEntry", owner.getEntryPoint().toString());
            }
            strings.add(s);
        }
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(new File(outRoot, module + ".strings.json")), "UTF-8")) {
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(strings));
        }

        println("[ExportDecompiled] " + module + ": " + total + " functions indexed, "
                + done + " decompiled, " + strings.size() + " strings, "
                + renamed + " symbols applied -> " + moduleDir.getAbsolutePath());
    }

    private int applySymbols(String path, Set<Long> exportAddrs) {
        int renamed = 0;
        try (FileReader reader = new FileReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            renamed += applyList(root.getAsJsonArray("exports"), exportAddrs, true);
            renamed += applyList(root.getAsJsonArray("imports"), exportAddrs, false);
        } catch (Exception ex) {
            println("[ExportDecompiled] symbol apply failed: " + ex.getMessage());
        }
        return renamed;
    }

    private int applyList(JsonArray list, Set<Long> exportAddrs, boolean isExport) {
        if (list == null) {
            return 0;
        }
        int n = 0;
        for (JsonElement element : list) {
            JsonObject o = element.getAsJsonObject();
            if (!o.has("name") || !o.has("vaddr")) {
                continue;
            }
            String name = o.get("name").getAsString();
            long vaddr = o.get("vaddr").getAsLong();
            if (name == null || name.isEmpty() || vaddr <= 0) {
                continue;
            }
            try {
                Address addr = toAddr(vaddr);
                Function function = getFunctionAt(addr);
                if (function != null) {
                    function.setName(name, SourceType.IMPORTED);
                    n++;
                } else {
                    createLabel(addr, name, true);
                    n++;
                }
                if (isExport) {
                    exportAddrs.add(vaddr);
                }
            } catch (Exception ignore) {
                // duplicate/invalid symbol; skip
            }
        }
        return n;
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
}
