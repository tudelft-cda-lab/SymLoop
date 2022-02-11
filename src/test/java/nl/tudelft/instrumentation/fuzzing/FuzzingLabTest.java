package nl.tudelft.instrumentation.fuzzing;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import nl.tudelft.instrumentation.branch.BranchCoverageVisitor;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FuzzingLabTest {

  @Test
  public void testBranchDistanceBoolFalse() {
    MyVar var = new MyVar(false);
    assertEquals(1, FuzzingLab.branchDistance(var, false));
    assertEquals(0, FuzzingLab.branchDistance(var, true));
  }
  @Test
  public void testBranchDistanceBoolTrue() {
    MyVar var = new MyVar(true);
    assertEquals(1, FuzzingLab.branchDistance(var, true));
    assertEquals(0, FuzzingLab.branchDistance(var, false));
  }
  @Test
  public void testBranchDistanceEqualInt() {
    MyVar var = new MyVar(new MyVar(1), new MyVar(10), "==");
    assertEquals(9, FuzzingLab.branchDistance(var, false));
    assertEquals(0, FuzzingLab.branchDistance(var, true));
  }
  @Test
  public void testBranchDistanceUnEqualInt() {
    MyVar var = new MyVar(new MyVar(10), new MyVar(10), "!=");
    assertEquals(1, FuzzingLab.branchDistance(var, false));
    assertEquals(0, FuzzingLab.branchDistance(var, true));
  }
  @Test
  public void testBranchDistanceAnd() {
    MyVar var = new MyVar(new MyVar(true), new MyVar(true), "&&");
    assertEquals(1, FuzzingLab.branchDistance(var, true));
    var = new MyVar(new MyVar(false), new MyVar(false), "&&");
    assertEquals(2, FuzzingLab.branchDistance(var, false));
    var = new MyVar(new MyVar(true), new MyVar(false), "&&");
    assertEquals(1, FuzzingLab.branchDistance(var, false));
    var = new MyVar(new MyVar(true), new MyVar(false), "&&");
    assertEquals(1, FuzzingLab.branchDistance(var, false));
  }
}
