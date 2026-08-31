import java.nio.file.*;
import java.net.URLClassLoader;
import javax.tools.ToolProvider;

/** Run from the project root with: java tools/RunFlowChecks.java */
class RunFlowChecks {
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Java 17 with compiler module is required");
        Path output = Files.createTempDirectory("check-tag-rs-flow-");
        try {
            int result = compiler.run(null, System.out, System.err, "--release", "17", "-Xlint:all", "-d", output.toString(),
                "app/src/main/java/com/tskforging/checktagrs/MultiKanbanFlow.java",
                "app/src/test/java/com/tskforging/checktagrs/MultiKanbanFlowChecks.java");
            if (result != 0) throw new IllegalStateException("Java compile failed: " + result);
            try (var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()})) {
                loader.loadClass("com.tskforging.checktagrs.MultiKanbanFlowChecks").getMethod("runAll").invoke(null);
            }
        } finally {
            try (var files = Files.walk(output)) {
                for (Path p : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }
}
