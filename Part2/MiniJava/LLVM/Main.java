import syntaxtree.*;
import visitor.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class Main {
    public static final String RED = "\033[0;31m";     // RED
    public static final String GREEN = "\033[0;32m";   // GREEN
    public static final String RESET = "\033[0m";  // Text Reset
    public static void main(String[] args) throws Exception {
        if(args.length < 1){
            System.err.println("Usage: java Main <inputFile>");
            System.exit(1);
        }

        FileInputStream fis = null;
        for(int i=0; i<args.length; i++){
            try{
                fis = new FileInputStream(args[i]);
                MiniJavaParser parser = new MiniJavaParser(fis);

                Goal root = parser.Goal();

                DeclarationVisitor eval = new DeclarationVisitor();
                root.accept(eval, null);
  
                String path = args[i].replace(".java", ".ll");
                File obj = new File(path);
                FileWriter writer = new FileWriter(path);

                eval.makeOffsets(eval.initClass);

                GenerateCode code = new GenerateCode(writer, eval.initClass, eval.varsInClass, eval.funcsInClass,
                                                    eval.varsInFuncs ,eval.offsetTableFuncs, eval.offsetTableVars);
                root.accept(code, null);
                writer.close();

            }
            catch(ParseException ex){
                System.out.println(ex.getMessage());
            }
            catch(FileNotFoundException ex){
                System.err.println(ex.getMessage());
            }
            // catch(Exception e){

            // }
            finally{
                try{
                    if(fis != null) fis.close();
                }
                catch(IOException ex){
                    System.err.println(ex.getMessage());
                }
            }
        }
    }
}

//simple data structure for counting the offsets of the classes
class Offsets {
    private int varOffset;
    private int funcOffset;

    Offsets(int varOffset, int funcOffset){
        this.varOffset = varOffset;
        this.funcOffset = funcOffset;
    }

    int getVarOffset(){ return this.varOffset; }
    int getFuncOffset() { return this.funcOffset; }

    int increaseVarOffset(int value) { return this.varOffset += value; }
    void increaseFuncOffset(int value) { this.funcOffset += value; }
}

//data structure for implementing pairs
class Pair {
    private final String lfield;
    private final String rfield;
    private final int hashCode;

    Pair(String lfield, String rfield){
        this.lfield = lfield;
        this.rfield = rfield;
        this.hashCode = Objects.hash(lfield, rfield);
    }

    String getRightField(){ return this.rfield; }
    String getLeftField() { return this.lfield; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Pair that = (Pair) obj;
        return rfield == that.rfield && lfield == that.lfield;
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }
}

class DeclarationVisitor extends GJDepthFirst<String, List<String>>{ //0st element = classname -> 1nd element = funcName for varFunc

    //Symbol Tables
    LinkedHashMap <String, String> initClass = new LinkedHashMap<String, String>(); //classes and their base class if exists
    LinkedHashMap<String, LinkedHashMap<String, String>> varsInClass = new LinkedHashMap<String, LinkedHashMap<String, String>>();
    LinkedHashMap<String, LinkedHashMap<String, Pair>> funcsInClass = new LinkedHashMap<String, LinkedHashMap<String, Pair>>();
    LinkedHashMap<Pair, LinkedHashMap<String, String>> varsInFuncs = new LinkedHashMap<Pair, LinkedHashMap<String, String>>();

    //offset Tables
    LinkedHashMap <String,Offsets> offsetTableCounter = new LinkedHashMap<String,Offsets>();    //helps to count the offsets
    LinkedHashMap<String, LinkedHashMap<String, Pair>> offsetTableVars = new LinkedHashMap<String, LinkedHashMap<String, Pair>>(); //storing the vars offsets
    LinkedHashMap<String, LinkedHashMap<String, Integer>> offsetTableFuncs = new LinkedHashMap<String, LinkedHashMap<String, Integer>>(); //storing the funcs offsets

    boolean searchFuncInheritance(String className, String funcName){
        String baseClass = initClass.get(className);
        if(baseClass != null){
            if(funcsInClass.get(baseClass).containsKey(funcName)){
                return true;
            }
            if(initClass.get(baseClass) != null)
                return searchFuncInheritance(baseClass, funcName);
        }
        return false;
    }

    //prints and computes the offsets
    public void makeOffsets(LinkedHashMap<String, String> map){

        for (Map.Entry<String, String> classes : map.entrySet()) {

            String className = classes.getKey();
            offsetTableVars.put(className, new LinkedHashMap<String, Pair>());
            offsetTableFuncs.put(className, new LinkedHashMap<String, Integer>());
            offsetTableCounter.put(className, new Offsets(0, 0));
            // System.out.println();
            // System.out.println("-----------Class " + className + " -----------");
            // System.out.println("---Variables---");

            if(varsInClass.containsKey(className)){
                if(initClass.get(className) != null)
                    offsetTableCounter.get(className).increaseVarOffset(offsetTableCounter.get(initClass.get(className)).getVarOffset());

                LinkedHashMap<String, String> vars = varsInClass.get(className);
                for (Map.Entry<String, String> var : vars.entrySet()) {

                    Integer start = offsetTableCounter.get(className).getVarOffset();
                    Integer end;

                    //System.out.println(className + "." + var.getKey() + " : " + offsetTableCounter.get(className).getVarOffset());

                    if(var.getValue().equals("int"))
                        end = offsetTableCounter.get(className).increaseVarOffset(4);
                    else if(var.getValue().equals("boolean"))
                        end = offsetTableCounter.get(className).increaseVarOffset(1);
                    else if(var.getValue().equals("int[]") || var.getValue().equals("boolean[]"))
                        end = offsetTableCounter.get(className).increaseVarOffset(16);
                    else
                        end = offsetTableCounter.get(className).increaseVarOffset(8);
                    
                    offsetTableVars.get(className).put(var.getKey(), new Pair(start.toString(), end.toString()));

                }
            }
            //System.out.println("---Methods---");
            if(funcsInClass.containsKey(className)){

                if(initClass.get(className) != null){
                    offsetTableCounter.get(className).increaseFuncOffset(offsetTableCounter.get(initClass.get(className)).getFuncOffset());
                    LinkedHashMap<String, Integer> src = offsetTableFuncs.get(initClass.get(className));
                    LinkedHashMap<String, Integer> dest = offsetTableFuncs.get(className);
                    dest.putAll(src);
                }

                LinkedHashMap <String, Pair> funcs = funcsInClass.get(className);
                for (Map.Entry<String, Pair> func : funcs.entrySet()){

                    if(func.getKey().equals("main"))
                        continue;
                    
                    if(searchFuncInheritance(className, func.getKey()))
                        continue;

                    offsetTableFuncs.get(className).put(func.getKey(), offsetTableCounter.get(className).getFuncOffset());
                    //System.out.println(className + "." + func.getKey() + " : " + offsetTableCounter.get(className).getFuncOffset());
                    offsetTableCounter.get(className).increaseFuncOffset(8);

                }
            }

        }

        //System.out.println();
    }

    public void printVarsInClasses(LinkedHashMap<String, LinkedHashMap<String, String>> map){

        for (Map.Entry<String, LinkedHashMap<String, String>> outer : map.entrySet()) {
            System.out.println("className: " + outer.getKey());
            for (Map.Entry<String, String> inner : outer.getValue().entrySet()) {
                System.out.println("\tvar: " + inner.getKey() + " type: " + inner.getValue());
            }
            System.out.println();
        }
    }

    public void printFuncsInClasses(LinkedHashMap<String, LinkedHashMap<String, Pair>> map){

        for (Map.Entry<String, LinkedHashMap<String, Pair>> outer : map.entrySet()) {
            System.out.println("className: " + outer.getKey());
            for (Map.Entry<String, Pair> inner : outer.getValue().entrySet()) {
                System.out.println("\tfunc: " + inner.getKey() + " " + inner.getValue().getLeftField() + " "
                + inner.getValue().getRightField());
            }
            System.out.println();
        }
    }

    public void printVarsInFuncs(LinkedHashMap<Pair, LinkedHashMap<String, String>> map){

        for (Map.Entry<Pair, LinkedHashMap<String, String>> outer : map.entrySet()) {
            System.out.println("func: " + outer.getKey().getLeftField() + " classname " + outer.getKey().getRightField());
            for (Map.Entry<String, String> inner : outer.getValue().entrySet()) {
                System.out.println("\tvar: " + inner.getKey() + " type " + inner.getValue());
            }
            System.out.println();
        }
    }

    public void initOfClass(String className, String extendsTo) throws Exception{
        if(initClass.containsKey(className))
            throw new Exception("Already declared class <" + className + "> \n");
        
        if(extendsTo != null && !initClass.containsKey(extendsTo))
            throw new Exception("Extends uninitialized class <" + extendsTo + "> \n");
        
        initClass.put(className, extendsTo);
    }

    public void addVarInFunc(String funcName, String className, String varName, String varType) throws Exception{
        Pair pair = new Pair(funcName, className);

        if(!varsInFuncs.containsKey(pair))
            varsInFuncs.put(pair, new LinkedHashMap<String, String>());
        
        LinkedHashMap<String, String> vars = varsInFuncs.get(pair);
        if(vars.containsKey(varName))
            throw new Exception("Already declared variable <" + varName + "> with type <" + vars.get(varName) +
            "> in function " + funcName + "\n");
        
        vars.put(varName, varType);
    }

    public void addVarInClass(String className, String varName, String varType) throws Exception{
        if(!varsInClass.containsKey(className))
            varsInClass.put(className, new LinkedHashMap<String, String>());
        
        LinkedHashMap<String, String> vars = varsInClass.get(className);
        if(vars.containsKey(varName))
                throw new Exception("Already declared variable<" + varName + "> with type <" + vars.get(varType) +
                "> in Class " + className + "\n");

        vars.put(varName, varType);
    }

    public void addFuncInClass(String funcName, String baseClass, String className, String args, String funcType) throws Exception{

        if(className.equals(baseClass)){
            if(!funcsInClass.containsKey(className))
                funcsInClass.put(className, new LinkedHashMap<String, Pair>());
        
            if(funcsInClass.get(className).containsKey(funcName))
                throw new Exception("Already declared function " + funcName + " in class " + className);
            
            funcsInClass.get(className).put(funcName, new Pair(funcType,args));
        }

        String extendsTo = initClass.get(className);
        if(extendsTo != null  && funcsInClass.containsKey(extendsTo) && funcsInClass.get(extendsTo).containsKey(funcName)){ 
            Pair extendedFunc = funcsInClass.get(extendsTo).get(funcName);

            if(!extendedFunc.getLeftField().equals(funcType))
                throw new Exception("Already declared function " + funcName + " with type " + extendedFunc.getLeftField() +
                " in base class " + extendsTo);

            String[] extendedArgArray = extendedFunc.getRightField().split(",");
            String[] argArray = args.split(",");

            if(argArray.length == extendedArgArray.length){
                for(int i = 0; i < argArray.length; i++){
                    String arg1[] = argArray[i].trim().split("\\s+");
                    String arg2[] = extendedArgArray[i].trim().split("\\s+");

                    if(!arg1[0].equals(arg2[0]))
                        throw new Exception("Already declared function " + funcName + " in base class " + extendsTo +
                        " with diffent type of arguments");

                    if(arg1.length == 2)
                        addVarInFunc(funcName, baseClass, arg1[1], arg1[0]);
                    else if(arg1.length == 3)
                        addVarInFunc(funcName, baseClass, arg1[2], arg1[1]+arg1[0]);
                }
            }           
            else{
                throw new Exception("Already declared function " + funcName + " in base class " + extendsTo + " with " +
                extendedArgArray.length + " arguments");
            }

        }
        else if(extendsTo != null && initClass.get(extendsTo) != null){
            addFuncInClass(funcName, baseClass, initClass.get(extendsTo), args, funcType);
        }
        else{
            if(args != ""){
                String[] argArray = args.split(",");
                for(int i = 0; i < argArray.length; i++){
                    String arg[] = argArray[i].trim().split("\\s+");

                    if(arg.length == 2)
                        addVarInFunc(funcName, baseClass, arg[1], arg[0]);
                    else
                        addVarInFunc(funcName, baseClass, arg[2], arg[0]+arg[1]);
                }              
            }
        }
    }

    /**
    * f0 -> MainClass()
    * f1 -> ( TypeDeclaration() )*
    * f2 -> <EOF>
    */
    @Override
    public String visit(Goal n, List<String> argu) throws Exception {
        n.f0.accept(this, null);
        n.f1.accept(this, null);
        n.f2.accept(this, null);

        //printFuncsInClasses(funcsInClass);
        //printVarsInClasses(varsInClass);
        //printVarsInFuncs(varsInFuncs);

        return null;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
    */
    @Override
    public String visit(MainClass n, List<String> argu) throws Exception {
        String className = n.f1.accept(this, null);
        String arg = n.f11.accept(this, null);

        initOfClass(className, null);
        addFuncInClass("main",className, className, "String[] " + arg, "void");

        n.f14.accept(this, Arrays.asList(className,"main"));
        n.f15.accept(this, null);

        return null;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
    */
    @Override
    public String visit(ClassDeclaration n, List<String> argu) throws Exception {
        String className = n.f1.accept(this, null);

        initOfClass(className, null);

        n.f3.accept(this, Arrays.asList(className, null));
        n.f4.accept(this, Arrays.asList(className, null));

        return null;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    @Override
    public String visit(ClassExtendsDeclaration n, List<String> argu) throws Exception {
        String className = n.f1.accept(this, null);
        String extendsTo = n.f3.accept(this, null);

        initOfClass(className, extendsTo);

        n.f5.accept(this, Arrays.asList(className, null));
        n.f6.accept(this, Arrays.asList(className, null));

        return null;
    }

    /**
    * f0 -> Type()
    * f1 -> Identifier()
    * f2 -> ";"
    */
    @Override
    public String visit(VarDeclaration n, List<String> argu) throws Exception {
        String className = argu.get(0);
        String funcName = argu.get(1);

        String type = n.f0.accept(this, null);
        String identifier = n.f1.accept(this, null);
  
        if(funcName != null)
            addVarInFunc(funcName, className, identifier, type);
        else
            addVarInClass(className, identifier, type);

        return null;
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     */
    @Override
    public String visit(MethodDeclaration n, List<String> argu) throws Exception {
        String className = argu.get(0);

        String argumentList = n.f4.present() ? n.f4.accept(this, null) : "";
        String funcType = n.f1.accept(this, null);
        String funcName = n.f2.accept(this, null);
        addFuncInClass(funcName, className, className, argumentList, funcType);

        n.f7.accept(this, Arrays.asList(className,funcName));
        n.f8.accept(this, null);
        
        n.f10.accept(this, null);

        return null;
    }

    @Override
    public String visit(Expression n, List<String> argu) throws Exception {
        return n.f0.accept(this, null);
    }

    @Override
    public String visit(AndExpression n, List<String> argu) throws Exception {
        String clause1 = n.f0.accept(this, null);
        String clause2 = n.f2.accept(this, null);
        return clause1 + " && " + clause2;
    }

    @Override
    public String visit(CompareExpression n, List<String> argu) throws Exception {
        String primary1 = n.f0.accept(this, null);
        String primary2 = n.f2.accept(this, null);
        return primary1 + " < " + primary2;
    }

    /**
    * f0 -> NotExpression()
    *       | PrimaryExpression()
    */
    @Override
    public String visit(Clause n, List<String> argu) throws Exception {
        return n.f0.accept(this, null);
    }

    @Override
    public String visit(PlusExpression n, List<String> argu) throws Exception {
        String primary1 = n.f0.accept(this, null);
        String primary2 = n.f2.accept(this, null);
        return primary1 + " + " + primary2;
    }

    @Override
    public String visit(MinusExpression n, List<String> argu) throws Exception {
        String primary1 = n.f0.accept(this, null);
        String primary2 = n.f2.accept(this, null);
        return primary1 + " - " + primary2;
    }

    @Override
    public String visit(TimesExpression n, List<String> argu) throws Exception {
        String primary1 = n.f0.accept(this, null);
        String primary2 = n.f2.accept(this, null);
        return primary1 + " * " + primary2;
    }

    /**
    * f0 -> PrimaryExpression()
    * f1 -> "["
    * f2 -> PrimaryExpression()
    * f3 -> "]"
    */
    @Override
    public String visit(ArrayLookup n, List<String> argu) throws Exception {
        String primary1 = n.f0.accept(this, null);
        String primary2 = n.f2.accept(this, null);
        return primary1 + "[" + primary2 + "]";
    }

    @Override
    public String visit(ArrayLength n, List<String> argu) throws Exception {
        String primary = n.f0.accept(this, null);
        return primary + ".length";
    }

    @Override
    public String visit(MessageSend n, List<String> argu) throws Exception {
        String primary = n.f0.accept(this, null);
        String identifier = n.f2.accept(this, null);
        String exprList =  n.f4.accept(this, null);
        return primary + "." + identifier + "(" + exprList + ")";
    }

    /**
    * f0 -> Expression()
    * f1 -> ExpressionTail()
    */
    @Override
    public String visit(ExpressionList n, List<String> argu) throws Exception {
        String expr = n.f0.accept(this, null);

        String nexpr = n.f1.accept(this, null);
        if (nexpr != null)
            return expr + nexpr;

        return expr;
    }

    /**
    * f0 -> ( ExpressionTerm() )*
    */
    @Override
    public String visit(ExpressionTail n, List<String> argu) throws Exception {
        return  n.f0.accept(this, null);
    }

    /**
    * f0 -> ","
    * f1 -> Expression()
    */
    @Override
    public String visit(ExpressionTerm n, List<String> argu) throws Exception {
        String expr = n.f1.accept(this, null);
        return "," + expr;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    @Override
    public String visit(FormalParameterList n, List<String> argu) throws Exception {
        String ret = n.f0.accept(this, null);

        if (n.f1 != null) {
            ret += n.f1.accept(this, null);
        }

        return ret;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    @Override
    public String visit(FormalParameterTerm n, List<String> argu) throws Exception {
        return n.f1.accept(this, argu);
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     */
    @Override
    public String visit(FormalParameterTail n, List<String> argu) throws Exception {
        String ret = "";
        for ( Node node: n.f0.nodes) {
            ret += ", " + node.accept(this, null);
        }

        return ret;
    }

    @Override
    public String visit(Statement n, List<String> argu) throws Exception {
        return n.f0.accept(this, null);
    }

    /**
    * f0 -> "{"
    * f1 -> ( Statement() )*
    * f2 -> "}"
    */
    @Override
    public String visit(Block n, List<String> argu) throws Exception {
        n.f1.accept(this, null);

        return null;
    }

    /**
    * f0 -> Identifier()
    * f1 -> "="
    * f2 -> Expression()
    * f3 -> ";"
    */
    @Override
    public String visit(AssignmentStatement n, List<String> argu) throws Exception {
        n.f0.accept(this, null);
        n.f2.accept(this, null);
        
        return null;
    }
  
    /**
     * f0 -> Identifier()
    * f1 -> "["
    * f2 -> Expression()
    * f3 -> "]"
    * f4 -> "="
    * f5 -> Expression()
    * f6 -> ";"
    */
    @Override
    public String visit(ArrayAssignmentStatement n, List<String> argu) throws Exception {
        n.f0.accept(this, null);
        n.f2.accept(this, null);
        n.f5.accept(this, null);

        return null;
    }
  
    /**
     * f0 -> "if"
    * f1 -> "("
    * f2 -> Expression()
    * f3 -> ")"
    * f4 -> Statement()
    * f5 -> "else"
    * f6 -> Statement()
    */
    @Override
     public String visit(IfStatement n, List<String> argu) throws Exception {
        n.f2.accept(this, null);
        n.f4.accept(this, null);
        n.f6.accept(this, null);

        return null;
     }
  
    /**
     * f0 -> "while"
    * f1 -> "("
    * f2 -> Expression()
    * f3 -> ")"
    * f4 -> Statement()
    */
    @Override
    public String visit(WhileStatement n, List<String> argu) throws Exception {
        n.f2.accept(this, null);
        n.f4.accept(this, null);

        return null;
    }
  
    /**
     * f0 -> "System.out.println"
    * f1 -> "("
    * f2 -> Expression()
    * f3 -> ")"
    * f4 -> ";"
    */
    @Override
    public String visit(PrintStatement n, List<String> argu) throws Exception {
        n.f2.accept(this, null);

        return null;
    }
  
    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    @Override
    public String visit(FormalParameter n, List<String> argu) throws Exception{
        String type = n.f0.accept(this, null);
        String name = n.f1.accept(this, null);
        return type + " " + name;
    }

    @Override
    public String visit(IntegerArrayType n, List<String> argu) {
        return "int[]";
    }

    @Override
    public String visit(BooleanArrayType n, List<String> argu) {
        return "boolean[]";
    }

    @Override
    public String visit(BooleanType n, List<String> argu) {
        return "boolean";
    }

    @Override
    public String visit(IntegerType n, List<String> argu) {
        return "int";
    }

    @Override
    public String visit(Identifier n, List<String> argu) {
        return n.f0.toString();
    }

    @Override
    public String visit(NotExpression n, List<String> argu) throws Exception {
        String clause = n.f1.accept(this, null);
        return "!" + clause;
    }

    @Override
    public String visit(PrimaryExpression n, List<String> argu) throws Exception {
        return n.f0.accept(this, null);
    }

    @Override
    public String visit(IntegerLiteral n, List<String> argu) throws Exception {
        return n.f0.toString();
    }

    @Override
    public String visit(TrueLiteral n, List<String> argu) throws Exception {
        return "true";
    }

    @Override
    public String visit(FalseLiteral n, List<String> argu) throws Exception {
        return "false";
    }

    @Override
    public String visit(ThisExpression n, List<String> argu) throws Exception {
        return "this";
    }

    @Override
    public String visit(ArrayAllocationExpression n, List<String> argu) throws Exception {
        return n.f0.accept(this, null);
    }

    @Override
    public String visit(BooleanArrayAllocationExpression n, List<String> argu) throws Exception {
        String expr = n.f3.accept(this, null);
        return "new boolean [" + expr + "]";    
    }

    @Override
    public String visit(IntegerArrayAllocationExpression n, List<String> argu) throws Exception {
        String expr = n.f3.accept(this, null);
        return "new int [" + expr + "]";   
    }

    @Override
    public String visit(AllocationExpression n, List<String> argu) throws Exception {
        String expr = n.f1.accept(this, null);
        return "new " + expr + "()";   
    }

    @Override
    public String visit(BracketExpression n, List<String> argu) throws Exception {
        String expr = n.f1.accept(this, null);
        return "( " + expr + " )";   
    }
}
