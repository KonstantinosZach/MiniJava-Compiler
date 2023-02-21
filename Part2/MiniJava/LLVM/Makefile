all: compile

compile:
	java -jar ../jtb132di.jar -te minijava.jj
	java -jar ../javacc5.jar minijava-jtb.jj
	javac Main.java

clean:
	rm -f *.class *~
	rm -f *.ll *~
	rm -f visitor/*.class *~
	rm -f syntaxtree/*.class *~

ll:
	clang -o out1 Example.ll

out:
	./out1

run:
	java Main Example.java