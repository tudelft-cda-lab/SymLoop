# SymLoop
This repository contains the implementation of the Symbolic Executor built for my master thesis.
It detects loops and unrolls these loops to find errors that requires many iterations through loops.

The repository is forked from: https://github.com/apanichella/JavaInstrumentation
This repository contains an implementation of a Java instrumentation tool to do source-code level instrumentation on the RERS 2020 problems. The instrumentation is done with the help of the JavaParser (https://github.com/javaparser/javaparser).

**NOTE:** This tool instruments only one Java file at a time.

# Build and run the tool
To build the project, make sure you have navigated to the root of this project and run the following Maven command:

`mvn clean package`

First of all, we need to use the following command to instrument a java file:

`java -cp target/aistr.jar nl.tudelft.instrumentation.Main --type=symbolic --file=Problem1.java > instrumented/Problem11.java`

Second of all, we need to add the Z3 library to the classpath to be able to do symbolic execution. We would then compile  using the following command:

`javac -cp target/aistr.jar:lib/com.microsoft.z3.jar:. instrumented/Problem11.java`

Finally, we also need to add the Z3 library to the classpath when running an instrumented Java file for the second lab:

`java -cp target/aistr.jar:lib/com.microsoft.z3.jar:./instrumented:. Problem11`

# Examples illustrating how to compile and run the instrumented files
In this section, we present you an example for each lab on how to instrument RERS problem and how to run the instrumented Java file. For the sake of simplicity, we will use the directory structure of this repository to how a RERS problem is instrumented. These examples do assume that the project has already been built using Maven.

### Compiling and running Reachability Problems
If you are compiling and running one of the Reachability Problems (Problem 11 - 19), make sure you also compile the Error class **together** with the instrumented file. We have included the `Errors.java` file for you in the root of the repository.

For compilation, you would need the following command:

`javac -cp target/aistr.jar:. Errors.java instrumented/Problem11.java`

And to run a Reachability problem:

`java -cp target/aistr.jar:./instrumented:. Problem11`

# Setting

The code has been tested with the following configuration:

* Maven 3.5.4
* Java 8
* JavaParser 3.18.0
