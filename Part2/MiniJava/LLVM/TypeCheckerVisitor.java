import syntaxtree.*;
import visitor.*;

import java.util.*;
import java.util.List;

public class TypeCheckerVisitor extends GJDepthFirst<String, List<String>>{
    LinkedHashMap<String, String> initClass;
    LinkedHashMap<String, LinkedHashMap<String, String>> varsInClass;
    LinkedHashMap<String, LinkedHashMap<String, Pair>> funcsInClass;
    LinkedHashMap<Pair, LinkedHashMap<String, String>> varsInFuncs;

    TypeCheckerVisitor(LinkedHashMap<String, String> initClass, LinkedHashMap<String, LinkedHashMap<String, String>> varsInClass,
                    LinkedHashMap<String, LinkedHashMap<String, Pair>> funcsInClass, LinkedHashMap<Pair, LinkedHashMap<String, String>> varsInFuncs){                      
        this.initClass = initClass;
        this.varsInClass = varsInClass;
        this.funcsInClass = funcsInClass;
        this.varsInFuncs = varsInFuncs;
        try {

            checkVarsInClasses();
            checkVarsInFuncs();
            checkFuncsInClasses();

        } catch (Exception e) {

            e.printStackTrace();

        }
    }

    void checkVarsInClasses() throws Exception{

        for (Map.Entry<String, LinkedHashMap<String, String>> outer : varsInClass.entrySet()) {
            String className = outer.getKey();

            for (Map.Entry<String, String> inner : outer.getValue().entrySet()) {
                String varName = inner.getKey();
                String varType = inner.getValue();
                
                if(varType.equals("int") || varType.equals("int[]") || 
                    varType.equals("boolean") || varType.equals("boolean[]"))
                        continue;
                
                if(!initClass.containsKey(varType))
                    throw new Exception("Unkhown type declaration for variable " + varName
                    + " in class " + className);
            }

        }

    }

    void checkVarsInFuncs() throws Exception{

        for (Map.Entry<Pair, LinkedHashMap<String, String>> outer : varsInFuncs.entrySet()) {
            String funcName = outer.getKey().getLeftField();

            for (Map.Entry<String, String> inner : outer.getValue().entrySet()) {
                String varName = inner.getKey();
                String varType = inner.getValue();

                if(varType.equals("int") || varType.equals("int[]") || 
                varType.equals("boolean") || varType.equals("boolean[]"))
                    continue;
                
                if(varType.equals("String[]") && funcName.equals("main"))
                    continue;
                
                if(!initClass.containsKey(varType))
                    throw new Exception("Unkhown type declaration for variable " + varName
                    + " in function " + funcName);                
            }

        }
             
    }

    void checkFuncsInClasses() throws Exception{

        for (Map.Entry<String, LinkedHashMap<String, Pair>> outer : funcsInClass.entrySet()) {
            String className = outer.getKey();

            for (Map.Entry<String, Pair> inner : outer.getValue().entrySet()) {
                String funcName = inner.getKey();
                String funcType = inner.getValue().getLeftField();

                if(funcType.equals("int") || funcType.equals("int[]") || 
                funcType.equals("boolean") || funcType.equals("boolean[]"))
                    continue;
                
                if(funcType.equals("void") && funcName.equals("main"))
                    continue;
                
                if(!initClass.containsKey(funcType))
                    throw new Exception("Unkhown type declaration for function " + funcName
                    + " in class " + className);
            }
        }
    }

    boolean varExistInClass(String className, String varName, String wantedType){
        if(varsInClass.containsKey(className)){

            if(varsInClass.get(className).containsKey(varName)){
                String clauseType = varsInClass.get(className).get(varName);

                if(wantedType.equals("[]"))
                    return clauseType.equals("int[]") || clauseType.equals("boolean[]");
                
                if(wantedType.equals(""))
                    return true;
                
                return clauseType.equals(wantedType);
            }
        }

        String baseClass = initClass.get(className);
        if(baseClass != null){

            if(varsInClass.containsKey(baseClass)){

                if(varsInClass.get(baseClass).containsKey(varName)){
                    String clauseType = varsInClass.get(baseClass).get(varName);

                    if(wantedType.equals("[]"))
                        return clauseType.equals("int[]") || clauseType.equals("boolean[]");

                    if(wantedType.equals(""))
                        return true;

                    return clauseType.equals(wantedType);
                }
            }
        }

        return false;
    }

    boolean varExistInFunc(String className, String funcName, String varName, String wantedType){
        Pair pair = new Pair(funcName, className);

        if(varsInFuncs.containsKey(pair)){
            LinkedHashMap<String, String> func = varsInFuncs.get(pair);

            if(func.containsKey(varName)){
                String clauseType = func.get(varName);

                if(wantedType.equals("[]"))
                    return clauseType.equals("int[]") || clauseType.equals("boolean[]");

                if(wantedType.equals(""))
                    return true;
                
                try {

                    String extendsTo = null;
                    extendsTo = getVarType(className, funcName, varName);
                    return clauseType.equals(wantedType) || 
                    (initClass.containsKey(extendsTo) && initClass.get(extendsTo) != null && initClass.get(extendsTo).equals(wantedType));

                } catch (Exception e) {

                    e.printStackTrace();

                }
            }
        }

        String baseClass = initClass.get(className);
        if(baseClass != null){

            pair = new Pair(funcName, baseClass);

            if(varsInFuncs.containsKey(pair)){
                LinkedHashMap<String, String> func = varsInFuncs.get(pair);
                if(func.containsKey(varName)){
                    String clauseType = func.get(varName);
    
                    if(wantedType.equals("[]"))
                        return clauseType.equals("int[]") || clauseType.equals("boolean[]");
    
                    if(wantedType.equals(""))
                        return true;
                    
                    try {
    
                        String extendsTo = null;
                        extendsTo = getVarType(baseClass, funcName, varName);
                        return clauseType.equals(wantedType) || 
                        (initClass.containsKey(extendsTo) && initClass.get(extendsTo) != null && initClass.get(extendsTo).equals(wantedType));
    
                    } catch (Exception e) {
    
                        e.printStackTrace();
    
                    }
                }
            }

            if(initClass.get(baseClass) != null){
                return varExistInClass(initClass.get(baseClass), varName, wantedType);
            }
        }
        return false;
    }

    boolean funcExistInClass(String funcScope, String funcName, String wantedType){

        if(funcsInClass.containsKey(funcScope)){
            LinkedHashMap<String, Pair> funcs  = funcsInClass.get(funcScope);

            if(funcs.containsKey(funcName)){
                Pair pair = funcs.get(funcName);
                String funcType = pair.getLeftField();

                if(wantedType.equals(""))
                    return true;
                
                return wantedType.equals(funcType);
            }
        }

        String baseClass = initClass.get(funcScope);
        if(baseClass != null){
            if(funcsInClass.containsKey(baseClass)){
                if(funcsInClass.get(baseClass).containsKey(funcName)){
                    Pair pair = funcsInClass.get(baseClass).get(funcName);
                    String funcType = pair.getLeftField();

                    if(wantedType.equals(""))
                        return true;

                    return wantedType.equals(funcType);
                }
            }

            if(initClass.get(baseClass) != null){
                return funcExistInClass(initClass.get(baseClass), funcName, wantedType);
            }
        }

        return false;

    }

    String getVarType(String className, String funcName, String IDname) throws Exception{

        if(IDname.equals("this"))
            return className;
        
        Pair pair = new Pair(funcName, className);
        if(varsInFuncs.containsKey(pair)){
            LinkedHashMap<String, String> func = varsInFuncs.get(pair);
            if(func.containsKey(IDname)){
                return func.get(IDname);
            }
        }

        if(varsInClass.containsKey(className)){
            if(varsInClass.get(className).containsKey(IDname)){
                return varsInClass.get(className).get(IDname);
            }
        }

        String baseClass = initClass.get(className);
        if(baseClass != null){
            if(varsInClass.containsKey(baseClass)){
                if(varsInClass.get(baseClass).containsKey(IDname)){
                    return varsInClass.get(baseClass).get(IDname);
                }
            }

            if(initClass.get(baseClass) != null){
                return getVarType(initClass.get(baseClass) , funcName, IDname);
            }
        }
        throw new Exception("Variable not found");
    }

    String getFuncArgs(String className, String funcName) throws Exception{

        if(funcsInClass.containsKey(className)){
            LinkedHashMap<String, Pair> funcs  = funcsInClass.get(className);
            if(funcs.containsKey(funcName)){
                Pair pair = funcs.get(funcName);
                return pair.getRightField();   
            }
        }

        String baseClass = initClass.get(className);
        if(baseClass != null){
            if(funcsInClass.containsKey(baseClass)){
                if(funcsInClass.get(baseClass).containsKey(funcName)){
                    Pair pair = funcsInClass.get(baseClass).get(funcName);
                    return pair.getRightField();
  
                }
            }

            if(initClass.get(baseClass) != null){
                return getFuncArgs(initClass.get(baseClass), funcName);
            }
        }

        throw new Exception("Function not found");
    }

    String getFuncType(String className, String funcName) throws Exception{

        if(funcsInClass.containsKey(className)){
            LinkedHashMap<String, Pair> funcs  = funcsInClass.get(className);
            if(funcs.containsKey(funcName)){
                Pair pair = funcs.get(funcName);
                return pair.getLeftField();   
            }
        }

        String baseClass = initClass.get(className);
        if(baseClass != null){
            if(funcsInClass.containsKey(baseClass)){
                if(funcsInClass.get(baseClass).containsKey(funcName)){
                    Pair pair = funcsInClass.get(baseClass).get(funcName);
                    return pair.getLeftField();
  
                }
            }

            if(initClass.get(baseClass) != null){
                return getFuncType(initClass.get(baseClass), funcName);               
            }
        }

        throw new Exception("Function not found");
    }

    boolean funcExistInVar(String className, String funcName, String varName, String IDname, String wantedType) throws Exception{

        String varNameType = getVarType(className, funcName, varName);
        if(varNameType != null && initClass.containsKey(varNameType)){
            return funcExistInClass(varNameType, IDname, wantedType);
        }

        return false;

    }

    void checkAND(String clause, List<String> argu) throws Exception{
        if(clause.equals("true") || clause.equals("false") || clause.equals("boolean"))
            return;

        String className = argu.get(0);
        String funcName = argu.get(1);
        String wantedType = "boolean";
        
        if(!varExistInFunc(className, funcName, clause, wantedType) && !varExistInClass(className, clause, wantedType))
            throw new Exception("Type mismatch in clause");
        
    }

    void checkComparePlusMinusTimes(String primary, List<String> argu) throws Exception{
        if(primary.equals("int"))
            return;

        if(primary.matches("-?\\d+(\\.\\d+)?"))
            return;
    
        String className = argu.get(0);
        String funcName = argu.get(1);
        String wantedType = "int";
    
        if(!varExistInFunc(className, funcName, primary, wantedType) && !varExistInClass(className, primary, wantedType))
            throw new Exception("Type mismatch");
    }

    void checkArray(String primary, List<String> argu) throws Exception{
        String className = argu.get(0);
        String funcName = argu.get(1);

        if(!varExistInFunc(className, funcName, primary, "[]") && !varExistInClass(className, primary, "[]"))
            throw new Exception("Type mismatch");
    }
    void checkLookUp(String primary, List<String> argu) throws Exception{
        if(primary.matches("\\d+(\\.\\d+)?"))
            return;

        String className = argu.get(0);
        String funcName = argu.get(1);
        String wantedType = "int";

        if(!varExistInFunc(className, funcName, primary, wantedType) && !varExistInClass(className, primary, wantedType))
            throw new Exception("Type mismatch");
    }

    void checkMessageID(String primary, List<String> argu) throws Exception{
        if(primary.equals("this"))
            return;
        
        String className = argu.get(0);
        String funcName = argu.get(1);
        if((!varExistInFunc(className, funcName, primary, "") && !varExistInClass(className, primary, ""))
        && !initClass.containsKey(primary))
            throw new Exception("Type mismatch");
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

        n.f15.accept(this, Arrays.asList(className,"main"));

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

        //n.f3.accept(this, Arrays.asList(className, null));
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

        n.f6.accept(this, Arrays.asList(className, null));

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

        //String argumentList = n.f4.present() ? n.f4.accept(this, null) : "";
        String funcType = n.f1.accept(this, null);
        String funcName = n.f2.accept(this, null);

        //n.f7.accept(this, Arrays.asList(className,funcName));
        n.f8.accept(this, Arrays.asList(className,funcName));

        n.f10.accept(this, Arrays.asList(className,funcName,funcType));

        return null;
    }

    @Override
    public String visit(Expression n, List<String> argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    @Override
    public String visit(AndExpression n, List<String> argu) throws Exception {

        List<String> modifList = new ArrayList<>(argu);
        modifList.remove(2);
        modifList.add("boolean");

        String clause1 = n.f0.accept(this, modifList);
        checkAND(clause1, argu);

        String clause2 = n.f2.accept(this, modifList);
        checkAND(clause2, argu);

        if(argu.size() == 3 && !argu.get(2).equals("boolean"))
                throw new Exception("Type mismatch in return value");
                
        return "boolean";
    }

    @Override
    public String visit(CompareExpression n, List<String> argu) throws Exception {

        List<String> modifList = new ArrayList<>(argu);
        modifList.remove(2);
        modifList.add("int");

        String primary1 = n.f0.accept(this, modifList);
        checkComparePlusMinusTimes(primary1, argu);

        String primary2 = n.f2.accept(this, modifList);
        checkComparePlusMinusTimes(primary2, argu);

        if(argu.size() == 3 && !argu.get(2).equals("boolean"))
                throw new Exception("Type mismatch in return value");

        return "boolean";
    }

    /**
    * f0 -> NotExpression()
    *       | PrimaryExpression()
    */
    @Override
    public String visit(Clause n, List<String> argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    @Override
    public String visit(PlusExpression n, List<String> argu) throws Exception {

        List<String> modifList = new ArrayList<>(argu);
        modifList.remove(2);
        modifList.add("int");

        String primary1 = n.f0.accept(this, modifList);
        checkComparePlusMinusTimes(primary1, argu);

        String primary2 = n.f2.accept(this, modifList);
        checkComparePlusMinusTimes(primary2, argu);

        if(argu.size() == 3 && !argu.get(2).equals("int"))
                throw new Exception("Type mismatch in return value");
                
        return "int";
    }

    @Override
    public String visit(MinusExpression n, List<String> argu) throws Exception {
        List<String> modifList = new ArrayList<>(argu);
        modifList.remove(2);
        modifList.add("int");

        String primary1 = n.f0.accept(this, modifList);
        checkComparePlusMinusTimes(primary1, argu);

        String primary2 = n.f2.accept(this, modifList);
        checkComparePlusMinusTimes(primary2, argu);

        if(argu.size() == 3 && !argu.get(2).equals("int"))
                throw new Exception("Type mismatch in return value");
                
        return "int";
    }

    @Override
    public String visit(TimesExpression n, List<String> argu) throws Exception {
        List<String> modifList = new ArrayList<>(argu);
        modifList.remove(2);
        modifList.add("int");


        String primary1 = n.f0.accept(this, modifList);
        checkComparePlusMinusTimes(primary1, argu);

        String primary2 = n.f2.accept(this, modifList);
        checkComparePlusMinusTimes(primary2, argu);

        if(argu.size() == 3 && !argu.get(2).equals("int"))
                throw new Exception("Type mismatch in return value");
                
        return "int";
    }

    /**
    * f0 -> PrimaryExpression()
    * f1 -> "["
    * f2 -> PrimaryExpression()
    * f3 -> "]"
    */

    @Override
    public String visit(ArrayLookup n, List<String> argu) throws Exception {

        List<String> modifList = new ArrayList<>(argu);
        modifList.remove(2);
        modifList.add("[]");

        String primary1 = n.f0.accept(this, modifList);
        checkArray(primary1, argu);

        modifList.remove(modifList.size()-1);
        modifList.add("int");

        String primary2 = n.f2.accept(this, modifList);
        checkLookUp(primary2, argu);

        if(argu.size() == 3 && (!argu.get(2).equals("int") && !argu.get(2).equals("boolean")))
                throw new Exception("Type mismatch in return value");
                
        return argu.get(2);
    }

    @Override
    public String visit(ArrayLength n, List<String> argu) throws Exception {

        List<String> modifList = new ArrayList<>(argu);
        modifList.remove(2);
        modifList.add("[]");


        String primary = n.f0.accept(this, modifList);
        checkArray(primary, argu);

        if(argu.size() == 3 && !argu.get(2).equals("int"))
            throw new Exception("Type mismatch in return value");

        return "int";
    }

    @Override
    public String visit(MessageSend n, List<String> argu) throws Exception {

        List<String> modifList = new ArrayList<>(argu);
        modifList.remove(2);
        modifList.add("");

        String primary = n.f0.accept(this, modifList);
        checkMessageID(primary, argu);

        modifList.remove(2);
        modifList.add(argu.get(2));
        modifList.add(primary);

        String identifier = n.f2.accept(this, modifList);
        
        String IDtype = primary;

        if(!initClass.containsKey(primary))
            IDtype = getVarType(argu.get(0), argu.get(1), primary);
        else if(primary.equals("this"))
            IDtype = argu.get(0);
        
        String funcArgs = getFuncArgs(IDtype, identifier);
        
        modifList.remove(3);
        modifList.remove(2);
        modifList.add(funcArgs);

        String exprlList = "";
        exprlList = n.f4.accept(this, modifList);

        if(exprlList == null && !funcArgs.equals(""))
            throw new Exception("Mismatch in the number of args");
        
        if(exprlList != null && exprlList.split(",").length != funcArgs.split(",").length)
            throw new Exception("Mismatch in the number of args");
        
        return getFuncType(IDtype, identifier);
    }

    /**
    * f0 -> Expression()
    * f1 -> ExpressionTail()
    */
    @Override
    public String visit(ExpressionList n, List<String> argu) throws Exception {
        List<String> modifList = new ArrayList<>(argu);
        String args = argu.get(2);
        String argType;
        String expr = "";
        String nexrp = "";

        if(args != ""){
            String[] argArray = args.split(",");
            String arg[] = argArray[0].trim().split("\\s+");

            if(arg.length == 2)
                argType = arg[0];
            else
                argType = arg[0] + arg[1];
            
            modifList.remove(2);
            modifList.add(argType);

            if(n.f0 != null)
                expr = n.f0.accept(this, modifList);

            modifList.remove(2);
            modifList.add(args);
            
            if (n.f1 != null)
                nexrp = n.f1.accept(this, modifList);
        } 

        return expr + nexrp;
    }

    /**
    * f0 -> ( ExpressionTerm() )*
    */
    @Override
    public String visit(ExpressionTail n, List<String> argu) throws Exception {
        List<String> modifList = new ArrayList<>(argu);
        String ret = "";
        String args = argu.get(2);
        String argType = "";
        int count = 1;

        for (Node node: n.f0.nodes){

            String[] argArray = args.split(",");
            String arg[] = argArray[count].trim().split("\\s+");

            if(arg.length == 2)
                argType = arg[0];
            else
                argType = arg[0] + arg[1];
  
            modifList.remove(2);
            modifList.add(argType);

            ret += "," + node.accept(this, modifList);

            count++;

        }

        return ret;
    }

    /**
    * f0 -> ","
    * f1 -> Expression()
    */
    @Override
    public String visit(ExpressionTerm n, List<String> argu) throws Exception {
        return n.f1.accept(this, argu);
    }

    @Override
    public String visit(Statement n, List<String> argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
    * f0 -> "{"
    * f1 -> ( Statement() )*
    * f2 -> "}"
    */
    @Override
    public String visit(Block n, List<String> argu) throws Exception {
        return n.f1.accept(this, argu);
    }

    /**
    * f0 -> Identifier()
    * f1 -> "="
    * f2 -> Expression()
    * f3 -> ";"
    */
    @Override
    public String visit(AssignmentStatement n, List<String> argu) throws Exception {
        String varName = n.f0.accept(this, argu);
        String varType = getVarType(argu.get(0), argu.get(1), varName);

        List<String> modifList = new ArrayList<>(argu);
        modifList.add(varType);

        return n.f2.accept(this, modifList);
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
        String identifier = n.f0.accept(this, argu);
        String varType = getVarType(argu.get(0), argu.get(1), identifier);

        List<String> modifList = new ArrayList<>(argu);
        modifList.add("int");

        n.f2.accept(this, modifList);

        modifList.remove(2);
        varType = varType.replace("[]", "");

        modifList.add(varType);

        n.f5.accept(this, modifList);

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
        List<String> modifList = new ArrayList<>(argu);
        modifList.add("boolean");

        n.f2.accept(this, modifList);
        n.f4.accept(this, argu);
        n.f6.accept(this, argu);

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
        List<String> modifList = new ArrayList<>(argu);
        modifList.add("boolean");
        n.f2.accept(this, modifList);
        n.f4.accept(this, argu);

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
        List<String> modifList = new ArrayList<>(argu);
        modifList.add("int");
        n.f2.accept(this, modifList);

        return null;
    }
  
    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    // @Override
    // public String visit(FormalParameter n, List<String> argu) throws Exception{
    //     String type = n.f0.accept(this, null);
    //     String name = n.f1.accept(this, null);
    //     return type + " " + name;
    // }

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
    public String visit(Identifier n, List<String> argu) throws Exception{
        String IDname = n.f0.toString();

        if(argu != null && argu.size() == 3){
            String className = argu.get(0);
            String funcName = argu.get(1);
            String wantedType = argu.get(2);

            if(!varExistInFunc(className, funcName, IDname, wantedType) && !varExistInClass(className, IDname, wantedType))
                throw new Exception("Type mismatch");
        }
        else if(argu != null && argu.size() == 4){
            String className = argu.get(0);
            String funcName = argu.get(1);
            String wantedType = argu.get(2);
            String scope = argu.get(3);

            if(scope.equals("new") && initClass.containsKey(IDname)){
                if(wantedType.equals(""))
                    return IDname;

                String extendsTo = initClass.get(IDname);
                if(!wantedType.equals(IDname) && !wantedType.equals(extendsTo))
                    throw new Exception("Type mismatch in new allocation");
            }
            else if(scope.equals("this")){
                if(!funcExistInClass(className, IDname, wantedType))
                    throw new Exception("Type mismatch");
            }
            else{
                if(initClass.containsKey(scope)){
                    if(!funcExistInClass(scope, IDname, wantedType))
                        throw new Exception("Type mismatch");
                }
               else{
                    if(!funcExistInVar(className, funcName, scope, IDname, wantedType))
                        throw new Exception("Type mismatch");
               }
            }

        }

        return IDname;
    }

    @Override
    public String visit(NotExpression n, List<String> argu) throws Exception {
        return n.f1.accept(this, argu);
    }

    @Override
    public String visit(PrimaryExpression n, List<String> argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    @Override
    public String visit(IntegerLiteral n, List<String> argu) throws Exception {
        if(argu.size() == 3 && !argu.get(2).equals("int"))
            throw new Exception("Type mismatch");

        return n.f0.toString();
    }

    @Override
    public String visit(TrueLiteral n, List<String> argu) throws Exception {
        if(argu.size() == 3 && !argu.get(2).equals("boolean"))
            throw new Exception("Type mismatch");
        return "true";
    }

    @Override
    public String visit(FalseLiteral n, List<String> argu) throws Exception {
        if(argu.size() == 3 && !argu.get(2).equals("boolean"))
            throw new Exception("Type mismatch");
        return "false";
    }

    boolean checkThisInheritance(String extendsTo, String wantedType, String type){
        if(type.equals(wantedType))
            return true;
        
        if(extendsTo != null){
            if(extendsTo.equals(wantedType))
                return true;
            else if (initClass.get(extendsTo) != null)
                 return checkThisInheritance(initClass.get(extendsTo), wantedType, type);
        }   
        return false;
    }

    @Override
    public String visit(ThisExpression n, List<String> argu) throws Exception {
        if(argu.size() == 3 && !argu.get(2).equals("")){
            String type = argu.get(0);
            String wantedType = argu.get(2);
            String extendsTo = initClass.get(type);
            
            if(!checkThisInheritance(extendsTo, wantedType, type))
                throw new Exception("Type mismatch");
        }

        return "this";
    }

    @Override
    public String visit(ArrayAllocationExpression n, List<String> argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    @Override
    public String visit(BooleanArrayAllocationExpression n, List<String> argu) throws Exception {
        List<String> modifList = new ArrayList<>(argu);
        modifList.remove(2);
        modifList.add("int");
        return n.f3.accept(this, modifList);

    }

    @Override
    public String visit(IntegerArrayAllocationExpression n, List<String> argu) throws Exception {
        List<String> modifList = new ArrayList<>(argu);
        modifList.remove(2);
        modifList.add("int");
        return n.f3.accept(this, modifList);  
    }

    @Override
    public String visit(AllocationExpression n, List<String> argu) throws Exception {
        List<String> modifList = new ArrayList<>(argu);
        modifList.add("new");
        return n.f1.accept(this, modifList);
    }

    @Override
    public String visit(BracketExpression n, List<String> argu) throws Exception {
        return n.f1.accept(this, argu);
    }
}
