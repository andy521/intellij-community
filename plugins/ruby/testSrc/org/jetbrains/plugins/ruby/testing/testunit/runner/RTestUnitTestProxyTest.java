package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.execution.testframework.Filter;
import static org.jetbrains.plugins.ruby.testing.testunit.runner.states.TestStateInfo.Magnitude.*;
import org.jetbrains.plugins.ruby.testing.testunit.runner.states.TestStateInfo;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitTestProxyTest extends BaseRUnitTestsTestCase {

  public void testTestInstance() {
    mySimpleTest = createTestProxy("newTest");

    assertEquals("newTest", mySimpleTest.getName());
    assertEquals("newTest", mySimpleTest.toString());

    assertEmpty(mySimpleTest.getChildren());
    assertTrue(mySimpleTest.isLeaf());
    assertNull(mySimpleTest.getParent());

    assertFalse(mySimpleTest.wasLaunched());
    assertFalse(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());
  }

  public void testGetName() {
    mySimpleTest = createTestProxy("newTest");
    assertEquals("newTest", mySimpleTest.getName());

    mySuite = createSuiteProxy("newSuite");
    assertEquals("newSuite", mySuite.getName());

    mySuite.setParent(mySimpleTest);
    assertEquals("newTest", mySimpleTest.getName());
  }

  public void testGetName_trim() {
    mySimpleTest = createTestProxy(" newTest ");
    assertEquals(" newTest ", mySimpleTest.getName());
  }

  public void testSuiteInstance() {
    mySuite = createSuiteProxy("newSuite");

    assertEquals("newSuite", mySuite.getName());
    assertEquals("newSuite", mySuite.toString());

    assertEmpty(mySuite.getChildren());
    assertTrue(mySuite.isLeaf());
    assertNull(mySuite.getParent());

    assertFalse(mySuite.wasLaunched());
    assertFalse(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());

    mySuite.addChild(mySimpleTest);
    assertEquals("newSuite", mySuite.getName());
    assertEquals("newSuite", mySuite.toString());
    assertSameElements(mySuite.getChildren(), mySimpleTest);
    assertFalse(mySuite.isLeaf());
  }

  public void testIsRoot() {
    final RTestUnitTestProxy rootTest = createTestProxy("root");
    assertTrue(rootTest.isRoot());

    rootTest.addChild(mySimpleTest);

    assertFalse(mySimpleTest.isRoot());
  }

  public void testTestStarted() {
    mySimpleTest.setStarted();

    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isInProgress());

    assertFalse(mySimpleTest.isDefect());
  }

  public void testTestStarted_InSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    assertTrue(mySuite.wasLaunched());
    assertTrue(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());
    assertFalse(mySimpleTest.wasLaunched());
    assertFalse(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());

    mySimpleTest.setStarted();

    assertTrue(mySuite.wasLaunched());
    assertTrue(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());
  }

  public void testTestFinished() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();

    assertTrue(mySimpleTest.wasLaunched());

    assertFalse(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());
    assertFalse(mySuite.wasTerminated());
  }

  public void testTestFinished_InSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySimpleTest.setStarted();
    mySimpleTest.setFinished();

    assertTrue(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());
    assertFalse(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());

    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());
  }

  public void testTestFinished_InSuite_WrongOrder() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySimpleTest.setStarted();
    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());

    assertTrue(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());
  }

  public void testTestFailed() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());
    assertTrue(mySimpleTest.getMagnitudeInfo() == TestStateInfo.Magnitude.FAILED_INDEX);

    mySimpleTest.setFinished();

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());

    assertTrue(mySimpleTest.getMagnitudeInfo() == TestStateInfo.Magnitude.FAILED_INDEX);
  }

  public void testTestFailed_InSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());
    assertTrue(mySimpleTest.isDefect());

    mySimpleTest.setFinished();

    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    assertTrue(mySimpleTest.getMagnitudeInfo() == TestStateInfo.Magnitude.FAILED_INDEX);
    assertTrue(mySuite.getMagnitudeInfo() == TestStateInfo.Magnitude.FAILED_INDEX);
  }

  public void testTestError() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());
    assertTrue(mySimpleTest.getMagnitudeInfo() == TestStateInfo.Magnitude.ERROR_INDEX);

    mySimpleTest.setFinished();

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());

    assertTrue(mySimpleTest.getMagnitudeInfo() == TestStateInfo.Magnitude.ERROR_INDEX);
  }

  public void testTestError_InSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());
    assertTrue(mySimpleTest.isDefect());

    mySimpleTest.setFinished();

    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    assertTrue(mySimpleTest.getMagnitudeInfo() == TestStateInfo.Magnitude.ERROR_INDEX);
    assertTrue(mySuite.getMagnitudeInfo() == TestStateInfo.Magnitude.ERROR_INDEX);
  }

  public void testSuiteTerminated() {
    mySuite.setStarted();
    mySuite.setTerminated();

    assertFalse(mySuite.isInProgress());
    
    assertTrue(mySuite.wasLaunched());
    assertTrue(mySuite.isDefect());
    assertTrue(mySuite.wasTerminated());

    mySuite.setFinished();
    assertTrue(mySuite.wasTerminated());    
  }

  public void testSuiteTerminated_WithNotRunChild() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySuite.setTerminated();

    assertTrue(mySuite.wasTerminated());
    assertTrue(mySimpleTest.wasTerminated());
  }

  public void testSuiteTerminated_WithChildInProgrss() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);
    mySimpleTest.setStarted();

    mySuite.setTerminated();

    assertTrue(mySuite.wasTerminated());
    assertTrue(mySimpleTest.wasTerminated());
  }

  public void testSuiteTerminated_WithChildInFinalState() {
    final RTestUnitTestProxy testPassed = createTestProxy("passed");
    final RTestUnitTestProxy testFailed = createTestProxy("failed");
    final RTestUnitTestProxy testInProgress = createTestProxy("inProgress");

    mySuite.setStarted();

    mySuite.addChild(testPassed);
    testPassed.setStarted();
    testPassed.setFinished();

    mySuite.addChild(testFailed);
    testFailed.setStarted();
    testFailed.setTestFailed("", "", false);
    testFailed.setFinished();

    mySuite.addChild(testInProgress);
    testInProgress.setStarted();

    // Suite terminated
    mySuite.setTerminated();

    assertTrue(mySuite.wasTerminated());
    assertFalse(testPassed.wasTerminated());
    assertFalse(testFailed.wasTerminated());
    assertTrue(testInProgress.wasTerminated());
  }

  public void testTestTerminated() {
    mySimpleTest.setTerminated();

    assertTrue(mySimpleTest.isDefect());
    assertTrue(mySimpleTest.wasTerminated());
    assertTrue(mySimpleTest.wasLaunched());

    assertFalse(mySimpleTest.isInProgress());

    mySimpleTest.setFinished();
    assertTrue(mySimpleTest.wasTerminated());
  }

  public void testMagnitude() {
    assertEquals(NOT_RUN_INDEX.getValue(), mySuite.getMagnitude());

    final RTestUnitTestProxy passedTest = createTestProxy("passed");
    final RTestUnitTestProxy failedTest = createTestProxy("failed");
    mySuite.addChild(passedTest);
    mySuite.addChild(failedTest);

    assertEquals(NOT_RUN_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(NOT_RUN_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(NOT_RUN_INDEX.getValue(), failedTest.getMagnitude());

    mySuite.setStarted();
    assertEquals(RUNNING_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(NOT_RUN_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(NOT_RUN_INDEX.getValue(), failedTest.getMagnitude());

    passedTest.setStarted();
    assertEquals(RUNNING_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(RUNNING_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(NOT_RUN_INDEX.getValue(), failedTest.getMagnitude());

    passedTest.setFinished();
    assertEquals(RUNNING_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(PASSED_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(NOT_RUN_INDEX.getValue(), failedTest.getMagnitude());

    failedTest.setStarted();
    assertEquals(RUNNING_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(PASSED_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(RUNNING_INDEX.getValue(), failedTest.getMagnitude());

    failedTest.setTestFailed("", "", false);
    assertEquals(RUNNING_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(PASSED_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(FAILED_INDEX.getValue(), failedTest.getMagnitude());

    mySuite.setFinished();
    assertEquals(FAILED_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(PASSED_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(FAILED_INDEX.getValue(), failedTest.getMagnitude());
  }

  public void testMagnitude_Error() {
    assertEquals(NOT_RUN_INDEX.getValue(), mySuite.getMagnitude());

    final RTestUnitTestProxy passedTest = createTestProxy("passed");
    final RTestUnitTestProxy failedTest = createTestProxy("failed");
    final RTestUnitTestProxy errorTest = createTestProxy("error");
    mySuite.addChild(passedTest);
    mySuite.addChild(failedTest);
    mySuite.addChild(errorTest);

    mySuite.setStarted();
    passedTest.setStarted();
    passedTest.setFinished();
    failedTest.setStarted();
    failedTest.setTestFailed("", "", false);
    failedTest.setFinished();
    errorTest.setStarted();
    errorTest.setTestFailed("", "", true);
    errorTest.setFinished();

    assertEquals(RUNNING_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(PASSED_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(FAILED_INDEX.getValue(), failedTest.getMagnitude());
    assertEquals(ERROR_INDEX.getValue(), errorTest.getMagnitude());
  }

  public void testMagnitude_Terminated() {
    assertEquals(NOT_RUN_INDEX.getValue(), mySuite.getMagnitude());

    final RTestUnitTestProxy testProxy = createTestProxy("failed");
    mySuite.addChild(testProxy);

    assertEquals(NOT_RUN_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(NOT_RUN_INDEX.getValue(), testProxy.getMagnitude());

    mySuite.setStarted();
    mySuite.setTerminated();
    assertEquals(TERMINATED_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(TERMINATED_INDEX.getValue(), testProxy.getMagnitude());
  }

  public void testMagnitude_suiteWithoutTests() {
    final RTestUnitTestProxy noTests = createSuiteProxy("failedSuite");
    noTests.setStarted();
    noTests.setFinished();
    assertEquals(ERROR_INDEX.getValue(), noTests.getMagnitude());
  }

  public void testMagnitude_PassedSuite() {
    final RTestUnitTestProxy passedSuite = createSuiteProxy("passedSuite");
    final RTestUnitTestProxy passedSuiteTest = createTestProxy("test");
    passedSuite.setStarted();
    passedSuite.addChild(passedSuiteTest);
    passedSuiteTest.setStarted();
    passedSuiteTest.setFinished();
    passedSuite.setFinished();
    assertEquals(PASSED_INDEX.getValue(), passedSuite.getMagnitude());
  }

  public void testLocation() {
    assertNull(mySuite.getLocation(getProject()));

    mySuite.addChild(mySimpleTest);

    assertNull(mySuite.getLocation(getProject()));
    assertNull(mySimpleTest.getLocation(getProject()));
  }

  public void testNavigatable() {
    assertNull(mySuite.getDescriptor(null));

    mySuite.addChild(mySimpleTest);

    assertNull(mySuite.getDescriptor(null));
    assertNull(mySimpleTest.getDescriptor(null));
  }

  public void testShouldRun_Test() {
    assertTrue(mySimpleTest.shouldRun());
  }

  public void testShouldRun_Suite() {
    assertTrue(mySuite.shouldRun());

    mySuite.addChild(mySimpleTest);
    assertTrue(mySuite.shouldRun());

    mySimpleTest.setStarted();
    assertTrue(mySuite.shouldRun());
  }

  public void testShouldRun_StartedTest() {
    mySimpleTest.setStarted();
    assertTrue(mySimpleTest.shouldRun());
  }

  public void testShouldRun_StartedSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);
    assertTrue(mySuite.shouldRun());

    mySimpleTest.setStarted();
    assertTrue(mySuite.shouldRun());
  }

  public void testShouldRun_FailedTest() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertTrue(mySimpleTest.shouldRun());
  }

  public void testShouldRun_FailedSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);

    assertTrue(mySuite.shouldRun());
  }

  public void testShouldRun_ErrorSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);

    assertTrue(mySuite.shouldRun());
  }

  public void testShouldRun_PassedTest() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    assertTrue(mySimpleTest.shouldRun());
  }

  public void testShouldRun_PassedSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();

    assertTrue(mySuite.shouldRun());
  }

  public void testFilter() {
    assertEmpty(mySuite.getChildren(Filter.NO_FILTER));
    assertEmpty(mySuite.getChildren(null));

    mySuite.addChild(mySimpleTest);

    assertEquals(1, mySuite.getChildren(Filter.NO_FILTER).size());
    assertEquals(1, mySuite.getChildren(null).size());
  }

  public void testGetAllTests() {
    assertOneElement(mySuite.getAllTests());

    final RTestUnitTestProxy suite1 = createTestProxy("newTest");
    mySuite.addChild(suite1);
    final RTestUnitTestProxy test11 = createTestProxy("newTest");
    suite1.addChild(test11);

    final RTestUnitTestProxy suite2 = createTestProxy("newTest");
    suite1.addChild(suite2);
    final RTestUnitTestProxy test21 = createTestProxy("newTest");
    suite2.addChild(test21);
    final RTestUnitTestProxy test22 = createTestProxy("newTest");
    suite2.addChild(test22);

    assertEquals(6, mySuite.getAllTests().size());
    assertEquals(5, suite1.getAllTests().size());
    assertEquals(3, suite2.getAllTests().size());
    assertOneElement(test11.getAllTests());
    assertOneElement(test21.getAllTests());
  }

  public void testIsSuite() {
    assertFalse(mySimpleTest.isSuite());

    mySimpleTest.setStarted();
    assertFalse(mySimpleTest.isSuite());

    final RTestUnitTestProxy suite = mySuite;
    assertTrue(suite.isSuite());

    suite.setStarted();
    assertTrue(suite.isSuite());
  }

  public void testDuration_ForTest() {
    assertNull(mySimpleTest.getDuration());

    mySimpleTest.setDuration(0);
    assertEquals(0, mySimpleTest.getDuration().intValue());

    mySimpleTest.setDuration(10);
    assertEquals(10, mySimpleTest.getDuration().intValue());

    mySimpleTest.setDuration(5);
    assertEquals(5, mySimpleTest.getDuration().intValue());

    mySimpleTest.setDuration(-2);
    assertNull(mySimpleTest.getDuration());
  }

  public void testDuration_ForSuiteEmpty() {
    final RTestUnitTestProxy suite = createSuiteProxy("root");
    assertNull(suite.getDuration());
  }

  public void testSetDuration_Suite() {
    mySuite.setDuration(5);
    assertNull(mySuite.getDuration());

    final RTestUnitTestProxy test = createTestProxy("test", mySuite);
    test.setDuration(2);
    mySuite.setDuration(5);
    assertEquals(2, mySuite.getDuration().intValue());
  }

  public void testDuration_ForSuiteWithTests() {
    final RTestUnitTestProxy suite = createSuiteProxy("root");
    final RTestUnitTestProxy test1 = createTestProxy("test1", suite);
    final RTestUnitTestProxy test2 = createTestProxy("test2", suite);

    assertNull(suite.getDuration());

    test1.setDuration(5);
    assertEquals(5, suite.getDuration().intValue());

    test2.setDuration(6);
    assertEquals(11, suite.getDuration().intValue());
  }

  public void testDuration_OnFinished() {
    final RTestUnitTestProxy suite = createSuiteProxy("root");
    final RTestUnitTestProxy test = createTestProxy("test1", suite);

    assertNull(suite.getDuration());

    test.setDuration(5);
    assertEquals(5, suite.getDuration().intValue());

    test.setDuration(7);
    assertEquals(7, suite.getDuration().intValue());

    suite.setFinished();
    assertEquals(7, suite.getDuration().intValue());

    test.setDuration(8);
    assertEquals(8, suite.getDuration().intValue());
  }

  public void testDuration_OnTerminated() {
    final RTestUnitTestProxy suite = createSuiteProxy("root");
    final RTestUnitTestProxy test = createTestProxy("test1", suite);

    assertNull(suite.getDuration());

    test.setDuration(5);
    assertEquals(5, suite.getDuration().intValue());

    test.setDuration(7);
    assertEquals(7, suite.getDuration().intValue());

    suite.setTerminated();
    assertEquals(7, suite.getDuration().intValue());

    test.setDuration(8);
    assertEquals(8, suite.getDuration().intValue());
  }

  public void testDuration_ForSuiteWithSuites() {
    final RTestUnitTestProxy root = createSuiteProxy("root");
    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", root);
    final RTestUnitTestProxy suite2 = createSuiteProxy("suite2", root);

    final RTestUnitTestProxy test11 = createTestProxy("test11", suite1);
    final RTestUnitTestProxy test12 = createTestProxy("test12", suite1);
    final RTestUnitTestProxy test21 = createTestProxy("test21", suite2);

    test11.setDuration(5);
    assertEquals(5, root.getDuration().intValue());

    test12.setDuration(6);
    assertEquals(11, root.getDuration().intValue());

    test21.setDuration(9);
    assertEquals(20, root.getDuration().intValue());
  }
}
