package me.bechberger.jstall.util;

import me.bechberger.jstall.util.CompilerQueueParser.CompilerQueueSnapshot;
import me.bechberger.jstall.util.CompilerQueueParser.CompileTask;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CompilerQueueParser.
 */
public class CompilerQueueParserTest {

    @Test
    public void testParseEmptyOutput() {
        assertNull(CompilerQueueParser.parse(null));
        assertNull(CompilerQueueParser.parse(""));
        assertNull(CompilerQueueParser.parse("   "));
    }

    @Test
    public void testParseInvalidOutput() {
        assertNull(CompilerQueueParser.parse("Invalid output"));
        assertNull(CompilerQueueParser.parse("Some random text\nwithout proper format"));
    }

    @Test
    public void testParseEmptyQueues() {
        String output = """
            Current compiles:
            
            C1 compile queue:
            Empty
            
            C2 compile queue:
            Empty
            """;

        CompilerQueueSnapshot snapshot = CompilerQueueParser.parse(output);
        
        assertNotNull(snapshot);
        assertEquals(0, snapshot.activeCompiles().size());
        assertEquals(2, snapshot.queuesByName().size());
        assertEquals(0, snapshot.queuedCountForQueue("C1"));
        assertEquals(0, snapshot.queuedCountForQueue("C2"));
        assertEquals(0, snapshot.totalActiveCount());
        assertEquals(0, snapshot.totalQueuedCount());
    }

    @Test
    public void testParseActiveCompiles() {
        String output = """
            Current compiles:
            CompilerThread1  123  %s! b  2     java.lang.String.indexOf (42 bytes)
            CompilerThread2  456    !   2     com.example.Foo.bar (128 bytes)
            
            C1 compile queue:
            Empty
            
            C2 compile queue:
            Empty
            """;

        CompilerQueueSnapshot snapshot = CompilerQueueParser.parse(output);
        
        assertNotNull(snapshot);
        assertEquals(2, snapshot.activeCompiles().size());
        
        CompileTask task1 = snapshot.activeCompiles().get(0);
        assertEquals(123, task1.compileId());
        assertTrue(task1.isOsr());
        assertTrue(task1.isSynchronized());
        assertTrue(task1.hasExceptionHandler());
        assertTrue(task1.isBlocking());
        assertFalse(task1.isNative());
        assertEquals(2, task1.tier());
        assertEquals("java.lang.String.indexOf", task1.methodName());
        assertEquals(42, task1.bytes());

        CompileTask task2 = snapshot.activeCompiles().get(1);
        assertEquals(456, task2.compileId());
        assertFalse(task2.isOsr());
        assertFalse(task2.isSynchronized());
        assertTrue(task2.hasExceptionHandler());
        assertFalse(task2.isBlocking());
        assertEquals(2, task2.tier());
        assertEquals("com.example.Foo.bar", task2.methodName());
        assertEquals(128, task2.bytes());
    }

    @Test
    public void testParseQueuedTasks() {
        String output = """
            Current compiles:
            
            C1 compile queue:
            124  % s!  1     com.example.Foo.bar @ 10 (128 bytes)
            125    !   1     com.example.Bar.baz (64 bytes)
            
            C2 compile queue:
            126      -     java.util.HashMap.put (256 bytes)
            127  %   -     java.lang.Thread.run @ 5 (32 bytes)
            """;

        CompilerQueueSnapshot snapshot = CompilerQueueParser.parse(output);
        
        assertNotNull(snapshot);
        assertEquals(0, snapshot.activeCompiles().size());
        assertEquals(2, snapshot.queuesByName().size());
        
        List<CompileTask> c1Tasks = snapshot.queuesByName().get("C1");
        assertEquals(2, c1Tasks.size());
        
        CompileTask c1Task1 = c1Tasks.get(0);
        assertEquals(124, c1Task1.compileId());
        assertTrue(c1Task1.isOsr());
        assertTrue(c1Task1.isSynchronized());
        assertTrue(c1Task1.hasExceptionHandler());
        assertEquals(1, c1Task1.tier());
        assertEquals("com.example.Foo.bar", c1Task1.methodName());
        assertEquals(10, c1Task1.osrBci());
        assertEquals(128, c1Task1.bytes());

        List<CompileTask> c2Tasks = snapshot.queuesByName().get("C2");
        assertEquals(2, c2Tasks.size());
        
        CompileTask c2Task1 = c2Tasks.get(0);
        assertEquals(126, c2Task1.compileId());
        assertFalse(c2Task1.isOsr());
        assertNull(c2Task1.tier()); // "-" means no tier
        assertEquals("java.util.HashMap.put", c2Task1.methodName());
        assertEquals(256, c2Task1.bytes());
    }

    @Test
    public void testParseWithPidHeader() {
        String output = """
            12345:
            Current compiles:
            
            C1 compile queue:
            Empty
            """;

        CompilerQueueSnapshot snapshot = CompilerQueueParser.parse(output);
        
        assertNotNull(snapshot);
        assertEquals(0, snapshot.activeCompiles().size());
        assertEquals(1, snapshot.queuesByName().size());
    }

    @Test
    public void testParseNativeMethod() {
        String output = """
            Current compiles:
            
            C1 compile queue:
            100     n  1     java.lang.System.currentTimeMillis (native)
            """;

        CompilerQueueSnapshot snapshot = CompilerQueueParser.parse(output);
        
        assertNotNull(snapshot);
        List<CompileTask> c1Tasks = snapshot.queuesByName().get("C1");
        assertEquals(1, c1Tasks.size());
        
        CompileTask task = c1Tasks.get(0);
        assertEquals(100, task.compileId());
        assertTrue(task.isNative());
        assertTrue(task.isNativeMethod());
        assertEquals("java.lang.System.currentTimeMillis", task.methodName());
        assertNull(task.bytes());
    }

    @Test
    public void testParseMethodOnly() {
        String output = """
            Current compiles:
            
            C1 compile queue:
            200       1     (method)
            """;

        CompilerQueueSnapshot snapshot = CompilerQueueParser.parse(output);
        
        assertNotNull(snapshot);
        List<CompileTask> c1Tasks = snapshot.queuesByName().get("C1");
        assertEquals(1, c1Tasks.size());
        
        CompileTask task = c1Tasks.get(0);
        assertEquals(200, task.compileId());
        assertEquals("(method)", task.methodName());
    }

    @Test
    public void testParseComplexScenario() {
        String output = """
            Current compiles:
            C1 CompilerThread0  101    !   1     com.example.Worker.doWork (256 bytes)
            C2 CompilerThread1  102  %s! b  2     com.example.HotLoop.compute @ 42 (512 bytes)
            
            C1 compile queue:
            103       1     java.lang.StringBuilder.append (16 bytes)
            104    !   1     com.example.Service.process (128 bytes)
            
            C2 compile queue:
            105  %     2     com.example.Cache.get @ 10 (64 bytes)
            
            """;

        CompilerQueueSnapshot snapshot = CompilerQueueParser.parse(output);
        
        assertNotNull(snapshot);
        assertEquals(2, snapshot.totalActiveCount());
        assertEquals(3, snapshot.totalQueuedCount());
        assertEquals(2, snapshot.queuedCountForQueue("C1"));
        assertEquals(1, snapshot.queuedCountForQueue("C2"));
        
        // Verify active compiles
        CompileTask active1 = snapshot.activeCompiles().get(0);
        assertEquals(101, active1.compileId());
        assertTrue(active1.hasExceptionHandler());
        assertEquals("com.example.Worker.doWork", active1.methodName());
        
        CompileTask active2 = snapshot.activeCompiles().get(1);
        assertEquals(102, active2.compileId());
        assertTrue(active2.isOsr());
        assertTrue(active2.isSynchronized());
        assertTrue(active2.hasExceptionHandler());
        assertTrue(active2.isBlocking());
        assertEquals(42, active2.osrBci());
    }

    @Test
    public void testParseNoTieredCompilation() {
        // When tiered compilation is disabled, no tier field is present
        String output = """
            Current compiles:
            
            C2 compile queue:
            100       java.lang.String.indexOf (42 bytes)
            """;

        CompilerQueueSnapshot snapshot = CompilerQueueParser.parse(output);
        
        assertNotNull(snapshot);
        List<CompileTask> c2Tasks = snapshot.queuesByName().get("C2");
        assertEquals(1, c2Tasks.size());
        
        CompileTask task = c2Tasks.get(0);
        assertEquals(100, task.compileId());
        assertNull(task.tier()); // No tier when not tiered
        assertEquals("java.lang.String.indexOf", task.methodName());
    }

    @Test
    public void testFormatFlags() {
        CompileTask task = new CompileTask(
            123,
            true,  // OSR
            true,  // synchronized
            false, // no exception handler
            true,  // blocking
            false, // not native flag
            2,
            "test.Method",
            null,
            100,
            false
        );
        
        String flags = task.formatFlags();
        assertTrue(flags.contains("OSR"));
        assertTrue(flags.contains("sync"));
        assertTrue(flags.contains("blocking"));
        assertFalse(flags.contains("exc"));
    }

    @Test
    public void testTotalCounts() {
        String output = """
            Current compiles:
            Thread1  1    !   2     Method1 (10 bytes)
            Thread2  2    !   2     Method2 (20 bytes)
            
            C1 compile queue:
            3       1     Method3 (30 bytes)
            4       1     Method4 (40 bytes)
            5       1     Method5 (50 bytes)
            
            C2 compile queue:
            6       2     Method6 (60 bytes)
            """;

        CompilerQueueSnapshot snapshot = CompilerQueueParser.parse(output);
        
        assertNotNull(snapshot);
        assertEquals(2, snapshot.totalActiveCount());
        assertEquals(4, snapshot.totalQueuedCount());
        assertEquals(3, snapshot.queuedCountForQueue("C1"));
        assertEquals(1, snapshot.queuedCountForQueue("C2"));
    }
}
