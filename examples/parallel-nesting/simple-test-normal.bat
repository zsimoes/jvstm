del simple\*.class
javac -cp ..\..\target\jvstm-2.3.jar;. simple\SimpleParallelTest.java
java -cp ..\..\target\jvstm-2.3.jar;. simple.SimpleParallelTest
javac -cp ..\..\target\jvstm-2.3.jar;. simple\SimpleUnsafeTest.java
java -cp ..\..\target\jvstm-2.3.jar;. simple.SimpleUnsafeTest
del simple\*.class
