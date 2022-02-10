package nl.tudelft.instrumentation.fuzzing;

import java.util.*;
import java.util.Random;

/**
 * You should write your own solution using this class.
 */
public class FuzzingLab {
        static Random r = new Random();
        static List<String> currentTrace;
        static int traceLength = 10;
        static boolean isFinished = false;

        static void initialize(String[] inputSymbols){
                // Initialise a random trace from the input symbols of the problem.
                currentTrace = generateRandomTrace(inputSymbols);
        }

        static int stringDifference(String a, String b) {
          int index = 0;
          int difference = 0;
          while(index < a.length() && index < b.length()){
            difference += Math.abs(a.charAt(index) - b.charAt(index));
            index++;
          }
          while(index < a.length()){
            difference += a.charAt(index);
          }
          while(index < b.length()){
            difference += b.charAt(index);
          }
          return difference;
        }

        static int binaryOperatorDistance(MyVar condition, boolean value) {
          switch (condition.operator) {
            case "==":
              if(value) {
                  if (condition.left.type == TypeEnum.INT && condition.left.type == TypeEnum.INT) {
                      return condition.left.int_value == condition.right.int_value? 1 : 0;
                  } else if(condition.left.type == TypeEnum.STRING && condition.right.type == TypeEnum.STRING){
                      return condition.left.str_value.equals(condition.right.str_value)? 1 : 0;
                  } else if(condition.left.type == TypeEnum.BOOL && condition.right.type == TypeEnum.BOOL){
                      return condition.left.value == condition.right.value ? 1 : 0;
                  }
              } else {
                  if (condition.left.type == TypeEnum.INT && condition.right.type == TypeEnum.INT) {
                      return Math.abs(condition.left.int_value - condition.right.int_value);
                  } else if(condition.left.type == TypeEnum.STRING && condition.right.type == TypeEnum.STRING){
                      return stringDifference(condition.left.str_value, condition.right.str_value);
                  } else if(condition.left.type == TypeEnum.BOOL && condition.right.type == TypeEnum.BOOL){
                      return condition.left.value == condition.right.value ? 0 : 1;
                  }
              }

            case "&&":
              if (value) {
                return Math.min(branchDistance(condition.left, value), branchDistance(condition.right, value));
              } else {
                return branchDistance(condition.left, value) + branchDistance(condition.right, value);
              }
            case "!=":
                if(value) {
                    if (condition.left.type == TypeEnum.INT && condition.right.type == TypeEnum.INT) {
                        return Math.abs(condition.left.int_value - condition.right.int_value);
                    } else if (condition.left.type == TypeEnum.STRING && condition.right.type == TypeEnum.STRING) {
                        return stringDifference(condition.left.str_value, condition.right.str_value);
                    } else if (condition.left.type == TypeEnum.BOOL && condition.right.type == TypeEnum.BOOL) {
                        return condition.left.value == condition.right.value ? 0 : 1;
                    }

                } else {
                    if (condition.left.type == TypeEnum.INT && condition.right.type == TypeEnum.INT) {
                        return condition.left.int_value == condition.right.int_value ? 1 : 0;
                    } else if (condition.left.type == TypeEnum.STRING && condition.right.type == TypeEnum.STRING) {
                        return condition.left.str_value.equals(condition.right.str_value) ? 1 : 0;
                    } else if (condition.left.type == TypeEnum.BOOL && condition.right.type == TypeEnum.BOOL) {
                        return condition.left.value == condition.right.value ? 1 : 0;
                    }
                }
              case "<":
                  if(value) {
                      if (condition.left.type == TypeEnum.INT && condition.right.type == TypeEnum.INT) {
                          int a = condition.left.int_value;
                          int b = condition.right.int_value;
                          return a < b ? (b-a) : 0;
                      }
                  } else {
                      if (condition.left.type == TypeEnum.INT && condition.right.type == TypeEnum.INT) {
                          int a = condition.left.int_value;
                          int b = condition.right.int_value;
                          return a < b ? 0 : (a-b+1);
                      }
                  }
              case "<=":
                  if(value) {
                      if (condition.left.type == TypeEnum.INT && condition.right.type == TypeEnum.INT) {
                          int a = condition.left.int_value;
                          int b = condition.right.int_value;
                          return a <= b ? (b-a+1) : 0;
                      }
                  } else {
                      if (condition.left.type == TypeEnum.INT && condition.right.type == TypeEnum.INT) {
                          int a = condition.left.int_value;
                          int b = condition.right.int_value;
                          return a <= b ? 0 : (a-b);
                      }
                  }
              case ">":
                  if(value) {
                      if (condition.left.type == TypeEnum.INT && condition.right.type == TypeEnum.INT) {
                          int a = condition.left.int_value;
                          int b = condition.right.int_value;
                          return a > b ? (a-b) : 0;
                      }
                  } else {
                      if (condition.left.type == TypeEnum.INT && condition.right.type == TypeEnum.INT) {
                          int a = condition.left.int_value;
                          int b = condition.right.int_value;
                          return a > b ? 0 : (b-a+1);
                      }
                  }
              case ">=":
                  if(value) {
                      if (condition.left.type == TypeEnum.INT && condition.right.type == TypeEnum.INT) {
                          int a = condition.left.int_value;
                          int b = condition.right.int_value;
                          return a >= b ? (a-b+1) : 0;
                      }
                  } else {
                      if (condition.left.type == TypeEnum.INT && condition.right.type == TypeEnum.INT) {
                          int a = condition.left.int_value;
                          int b = condition.right.int_value;
                          return a >= b ? 0 : (b-a);
                      }
                  }
              case "||":
                  if(value){
                      return branchDistance(condition.left, value) + branchDistance(condition.right, value);
                  } else {
                      return Math.min(branchDistance(condition.left, value), branchDistance(condition.right, value));
                  }
              case "^":
                  if(value){
                      return Math.min(branchDistance(condition.left, value) + branchDistance(condition.right, !value),
                              branchDistance(condition.left, !value) + branchDistance(condition.right, value));
                  } else {
                      return Math.min(branchDistance(condition.left, value) + branchDistance(condition.right, value),
                              branchDistance(condition.left, !value) + branchDistance(condition.right, !value));
                  }
          }
          throw new AssertionError("not implemented yet, binaryOperatorDistance: " + condition.operator);
        }

        static int unaryOperatorDistance(MyVar condition, boolean value) {
          switch (condition.operator) {
            case "!":
              // TODO
               if (condition.left.type == TypeEnum.BOOL) {
                 if (value) {
                   return condition.left.value? 1 : 0;
                 } else {
                   return condition.left.value? 0 : 1;
                 }
               } else {
                 // TODO what should happen here
//                 return 1 - branchDistance(condition.left, value);
                   throw new AssertionError("not implemented yet, unaryOperatorDistance: " + condition.operator);
               }
          }
          return 0;
        }

        /**
         * Making a if-statement true: (value = false) (target = true)
         a : d = {0 if a is true, 1 otherwise}
         !a : d = {1 if a is true, 0 otherwise}
         a == b : d = abs(a-b)  CHECK
         a != b : d = {0 if a !=b, 1 otherwise} CHECK
         a < b : d = {0 if a < b; a-b + K otherwise} CHECK
         a <= b : d = {0 if a <= b; a-b otherwise} CHECK
         a > b : d = {0 if a > b; b-a + K otherwise} CHECK
         a >= b : d = {0 if a >= b; b - a otherwise} CHECK
         and for combinations of predicates:

         p1 && p2 : d = d(p1) + d(p2) CHECK
         p1 | p2 : d = min(d(p1), d(p2))
         p1 XOR p2 : d = min(d(p1) + d(!p2), d(!p1) + d(p2))
         !p1 : d = 1 - d(p1)

         * Making a if-statement false: (value = true) (target = false)
         a : d = {1 if a is true, 0 otherwise}
         !a : d = {0 if a is true, 1 otherwise}
         a == b : d = {0 if a != b, 1 otherwise} CHECK
         a != b : d = abs(a-b) CHECK
         a < b : d = {b-a if a < b; 0 otherwise} CHECK
         a <= b : d = {b-a+1 if a <= b; 0 otherwise} CHECK
         a > b : d = {a-b if a > b; 0 otherwise} CHECK
         a >= b : b = {a-+1 if a >= b; 0 otherwise} CHECK

         and for combinations of predicates:
         p1 & p2 : d = min(d(p1), d(p2)) CHECK
         p1 | p2 : d = d(p1) + d(p2) CHECK
         p1 XOR p2 : d = min(d(p1) + d(p2), d(!p1) + d(!p2)) CHECK
         !p1 : d = 1 - d(p1)
         */
        static int branchDistance(MyVar condition, boolean value) {
          switch(condition.type) {
            case BINARY:
              return binaryOperatorDistance(condition, value);
            case BOOL:
              if(value) {
                return condition.value? 1 : 0;
              } else {
                return condition.value? 0 : 1;
              }
            default:
              break;
          }
          throw new AssertionError("not implemented yet, branchDistance: " + condition.toString());
        }

        /**
         * Write your solution that specifies what should happen when a new branch has been found.
         */
        static void encounteredNewBranch(MyVar condition, boolean value, int line_nr) {
            int bd = branchDistance(condition, value);
            System.out.printf("line %5d, now: %b,\tdist: %2d, %s\n", line_nr, value, bd, condition.toString());
        }

        /**
         * Method for fuzzing new inputs for a program.
         * @param inputSymbols the inputSymbols to fuzz from.
         * @return a fuzzed sequence
         */
        static List<String> fuzz(String[] inputSymbols){
                /*
                 * Add here your code for fuzzing a new sequence for the RERS problem.
                 * You can guide your fuzzer to fuzz "smart" input sequences to cover
                 * more branches. Right now we just generate a complete random sequence
                 * using the given input symbols. Please change it to your own code.
                 */
                return generateRandomTrace(inputSymbols);
        }

        /**
         * Generate a random trace from an array of symbols.
         * @param symbols the symbols from which a trace should be generated from.
         * @return a random trace that is generated from the given symbols.
         */
        static List<String> generateRandomTrace(String[] symbols) {
                ArrayList<String> trace = new ArrayList<>();
                for (int i = 0; i < traceLength; i++) {
                        trace.add(symbols[r.nextInt(symbols.length)]);
                }
                return trace;
        }

        static void run() {
                initialize(DistanceTracker.inputSymbols);
                DistanceTracker.runNextFuzzedSequence(currentTrace.toArray(new String[0]));

                // Place here your code to guide your fuzzer with its search.
                while(!isFinished) {
                        // Do things!
                        try {
                                System.out.println("Woohoo, looping!");
                                Thread.sleep(1000);
                        } catch (InterruptedException e) {
                                e.printStackTrace();
                        }
                }
        }

        /**
         * Method that is used for catching the output from standard out.
         * You should write your own logic here.
         * @param out the string that has been outputted in the standard out.
         */
        public static void output(String out){
                System.out.println(out);
        }
}
