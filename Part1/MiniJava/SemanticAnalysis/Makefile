all: compile

compile:
	java -jar ../jtb132di.jar -te minijava.jj
	java -jar ../javacc5.jar minijava-jtb.jj
	javac Main.java
	javac TypeCheckerVisitor.java

clean:
	rm -f *.class *~
	rm -f visitor/*.class *~
	rm -f syntaxtree/*.class *~

