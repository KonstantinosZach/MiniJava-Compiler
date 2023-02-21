import syntaxtree.*;
import visitor.*;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//a class that helps to pass the information needed
class infoList{
    private List<String> list;
    Boolean idToReturn = false;

    infoList(String _className, String _funcName){
        List<String> _list = new ArrayList<String>();
        list = _list;

        idToReturn = false;
        list.add(_className);
        list.add(_funcName);
    }

    infoList(infoList srclist){
        List<String> _list = new ArrayList<String>();  
        list = _list;

        idToReturn = false;
        list.add(srclist.getClassName());
        list.add(srclist.getFuncName());
    }

    String getClassName(){ return list.get(0); }
    String getFuncName(){ return list.get(1); }
    Boolean getIdToReturn(){ return idToReturn; }

    String setClassName(String newClassName){ return list.set(0, newClassName); }
    String setFuncName(String newFuncName){ return list.set(1, newFuncName); }
    Boolean setIdToReturn(Boolean state){ return idToReturn = state; }
}

public class GenerateCode extends GJDepthFirst<String, infoList>{

    FileWriter writer;
    //first we use the rcounter and then we increment it
    Integer rcounter = 0;
    //labels counter for automatic creation
    Integer cExprRes = 0;
    Integer cIfThen = 0;
    Integer cIfElse = 0;
    Integer cIfEnd = 0;
    Integer cWhileStart = 0;
    Integer cWhileBody = 0;
    Integer cWhileExit = 0;
    Integer cNszOk = 0;
    Integer cNszError = 0;
    Integer cOobOk = 0;
    Integer cOobError = 0;

    //Symbol Tables and usefull data
    LinkedHashMap <String, String> initClass = new LinkedHashMap<String, String>();
    LinkedHashMap<String, LinkedHashMap<String, String>> varsInClass = new LinkedHashMap<String, LinkedHashMap<String, String>>();
    LinkedHashMap<String, LinkedHashMap<String, Pair>> funcsInClass = new LinkedHashMap<String, LinkedHashMap<String, Pair>>();
    LinkedHashMap<Pair, LinkedHashMap<String, String>> varsInFuncs = new LinkedHashMap<Pair, LinkedHashMap<String, String>>();
    LinkedHashMap<String, LinkedHashMap<String, Integer>> vTables = new LinkedHashMap<String, LinkedHashMap<String, Integer>>();
    LinkedHashMap<String, LinkedHashMap<String, Pair>> offsetTable = new LinkedHashMap<String, LinkedHashMap<String, Pair>>();
    //this new map will help with the mapping between the local variables and their register
    LinkedHashMap<Pair, LinkedHashMap<String, String>> regsInFuncs = new LinkedHashMap<Pair, LinkedHashMap<String, String>>();
    LinkedHashMap<String, String> regsTypes = new LinkedHashMap<String, String>();

    //converts minijava types to llvm types
    String typeConverter(String type){

        if(type.equals("i32") || type.equals("i1") || type.equals("i32*") || type.equals("i1*") || type.equals("i8*")
            || type.equals("%struct._IntegerArray") || type.equals("%struct._BooleanArray"))
            return type;

        switch(type){
            case "int": return "i32";
            case "boolean": return "i1";
            case "int[]": return "%struct._IntegerArray";
            case "boolean[]": return "%struct._BooleanArray";
            default: return "i8*";
        }
    }

    //creates a new label for expressions handling
    String createExprResLabel(){
        String newLabel = "%exp_res_" + cExprRes;
        cExprRes++;
        return newLabel;
    }

    //creates a new label for if then handling
    String createIfThenLabel(){
        String newLabel = "%if_then_" + cIfThen;
        cIfThen++;
        return newLabel;
    }

    //creates a new label for if else handling
    String createIfElseLabel(){
        String newLabel = "%if_else_" + cIfElse;
        cIfElse++;
        return newLabel;
    }

    //creates a new label for if else handling
    String createIfEndLabel(){
        String newLabel = "%if_end_" + cIfEnd;
        cIfEnd++;
        return newLabel;
    }

    String createWhileStart(){
        String newLabel = "%while_start_" + cWhileStart;
        cWhileStart++;
        return newLabel;
    }

    String createWhileBody(){
        String newLabel = "%while_body_" + cWhileBody;
        cWhileBody++;
        return newLabel;
    }

    String createWhileExit(){
        String newLabel = "%while_exit_" + cWhileExit;
        cWhileExit++;
        return newLabel;
    }

    String createcNszOk(){
        String newLabel = "%nsz_ok_" + cNszOk;
        cNszOk++;
        return newLabel;
    }

    String createcNszError(){
        String newLabel = "%nsz_err_" + cNszError;
        cNszError++;
        return newLabel;
    }
 
    String createcOobOk(){
        String newLabel = "%oob_ok_" + cOobOk;
        cOobOk++;
        return newLabel;
    }

    String createcOobError(){
        String newLabel = "%oob_err_" + cOobError;
        cOobError++;
        return newLabel;
    }
    
    String getArray(String idType, String idReg) throws Exception{
        Integer temp;
        writer.append("\t%_" + rcounter + " = getelementptr " + idType + ", " + idType + "* " + idReg + ", i32 0, i32 1\n");
        temp = rcounter;
        rcounter++;

        //different load for each array
        if(idType.equals("%struct._IntegerArray"))
            writer.append("\t%_" + rcounter + " = load i32*, i32** %_" + temp + "\n");
        else
            writer.append("\t%_" + rcounter + " = load i1*, i1** %_" + temp + "\n");

        temp = rcounter;
        rcounter++;
        return "%_" + temp.toString();
    }

    String getSizeOfArray(String idType, String idReg) throws Exception{
        Integer temp;
        writer.append("\t%_" + rcounter + " = getelementptr " + idType + ", " + idType + "* " + idReg + ", i32 0, i32 0\n");
        temp = rcounter;
        rcounter++;

        writer.append("\t%_" + rcounter + " = load i32, i32* %_" + temp + "\n");
        temp = rcounter;
        rcounter++;
        return "%_" + temp.toString();
    }

    Void checkOob(String pos, String sizeReg) throws Exception{
        Integer temp1, temp2;

        writer.append("\t%_" + rcounter + " = icmp sge " + pos + ", 0\n");
        temp1 = rcounter;
        rcounter++;

        writer.append("\t%_" + rcounter + " = icmp slt " + pos + ", " + sizeReg + "\n");
        temp2 = rcounter;
        rcounter++;

        writer.append("\t%_" + rcounter + " = and i1 " + "%_" + temp1 + ", %_" + temp2 + "\n");
        String obbError = createcOobError();
        String obbOk = createcOobOk();
        writer.append("\tbr i1 %_" + rcounter + ", label " + obbOk + ", label " + obbError + "\n");
        writer.append("\t" + obbError.replace("%", "") + ":\n");
        writer.append("\tcall void @throw_oob()\n");
        writer.append("\tbr label " + obbOk + "\n");
        writer.append("\t" + obbOk.replace("%", "") + ":\n");
        rcounter++;

        return null;
    }

    //writes all the needed var allocation in the beginning of a function
    //and inits the registers for them
    void writeAllocas(infoList list) throws Exception {

        //finds the vars of the function
        Pair pair = new Pair(list.getFuncName(), list.getClassName());
        LinkedHashMap<String, String> vars = varsInFuncs.get(pair);

        //if vars exist inside the function we allocate them and init their registers
        if(vars != null){
            //but also we init the register for the variable inside the function
            regsInFuncs.put(pair, new LinkedHashMap<String, String>());

            for (Map.Entry<String, String> var : vars.entrySet()){
                //we write the alloca
                writer.append("\t%" + var.getKey() + " = alloca " + typeConverter(var.getValue()) + "\n");
                regsInFuncs.get(pair).put(var.getKey(), "%" + var.getKey());
            }
        }
    }

    //returns the register of the given variable
    //returns null if it doesnt exist
    String getVarReg(infoList list, String varName){
        LinkedHashMap<String, String> vars = regsInFuncs.get(new Pair(list.getFuncName(), list.getClassName()));
        if(vars != null)
            return vars.get(varName);
        return null;
    }

    //we update the register of the given variable
    //returns true if succed otherwise false
    Boolean updateVarReg(infoList list, String varName, String newReg){
        LinkedHashMap<String, String> vars = regsInFuncs.get(new Pair(list.getFuncName(), list.getClassName()));
        if(vars != null)
            vars.remove(varName, newReg);
        return false;
    }

    //stores arguments of the function
    //we call this function after allocating the args (writeAllocas)
    Void storeArgs(String args) throws Exception{
        if(!args.equals("")){
            String[] argArray = args.split(",");
            //for each argument find the corresponding type
            for(String arg: argArray){
                if(!arg.equals("")){
                    String[] splitArg= arg.trim().split("\\s+");
                    writer.append("\tstore " + splitArg[0] + " " + splitArg[1] + ", " + splitArg[0] + "* " + splitArg[1].replace(".", "") + "\n");
                }
            }
        }
        
        return null;
    }

    //returns the llvm type of an existing variable
    //returns null if var does not exist
    String getVarTypeLLVM(infoList list, String IDname){
        String className = list.getClassName();
        String funcName = list.getFuncName();

        if(IDname.equals("i8* %this"))
            return typeConverter(className);
        
        Pair pair = new Pair(funcName, className);
        if(varsInFuncs.containsKey(pair)){
            LinkedHashMap<String, String> func = varsInFuncs.get(pair);
            if(func.containsKey(IDname)){
                return typeConverter(func.get(IDname));
            }
        }

        if(varsInClass.containsKey(className)){
            if(varsInClass.get(className).containsKey(IDname)){
                return typeConverter(varsInClass.get(className).get(IDname));
            }
        }

        String baseClass = initClass.get(className);
        if(baseClass != null){
            if(varsInClass.containsKey(baseClass)){
                if(varsInClass.get(baseClass).containsKey(IDname)){
                    return typeConverter(varsInClass.get(baseClass).get(IDname));
                }
            }

            if(initClass.get(baseClass) != null){
                list.setClassName(initClass.get(baseClass));
                return getVarTypeLLVM(list, IDname);
            }
        }

        return null;
    }

    String getVarType(infoList list, String IDname){
        String className = list.getClassName();
        String funcName = list.getFuncName();

        if(IDname.equals("i8* %this"))
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
                list.setClassName(initClass.get(baseClass));
                return getVarType(list, IDname);
            }
        }

        return null;
    }

    //returns the arglist of the func
    String getFuncArgs(infoList list){

        String className = list.getClassName();
        String funcName = list.getFuncName();

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
                list.setClassName(initClass.get(baseClass));
                return getFuncArgs(list);
            }
        }

        return null;
    }

    //returns the args of the function in llvm manner
    String convertFuncArgs(String args){
        StringBuffer argsbuffer = new StringBuffer();
        argsbuffer.append("");
        if(!args.equals("")){
            String[] argArray = args.split(",");

            //for each argument find the corresponding type
            for(String arg: argArray){
                String[] splitArg= arg.trim().split("\\s+");
                String argType;
                String id;

                if(splitArg.length == 2){
                    argType = splitArg[0];
                    id = splitArg[1];
                }
                else{
                    argType = splitArg[0] + splitArg[1];
                    id = splitArg[2];
                }
                argsbuffer.append(", " + typeConverter(argType) + " %." + id);
            }
        }
        return argsbuffer.toString();
    }

    //return the size of a class, gets the info from the offset table
    String sizeOfClass(String className){
        LinkedHashMap<String, Pair> table = offsetTable.get(className);
        Integer counter = 0;

        //if the current class does not have any fields we check to see its baseclass size
        if(table.isEmpty() && initClass.get(className) != null){
            return sizeOfClass(initClass.get(className));
        }
        else{
            //we dont increase the counter we just get the last value cause the map returns the final offset
            for (Map.Entry<String, Pair> t : table.entrySet())
                //basic +8 increase for vtable
                counter = Integer.parseInt(t.getValue().getRightField()) + 8;
        }

        return counter.toString();
    }

    //when we want to use a var which is declared inside a class
    //we need to khow its position from the offset table of that class
    String getPositionOfVar(String className, String varName){
        LinkedHashMap<String, Pair> table = offsetTable.get(className);

        if(table.isEmpty() && initClass.get(className) != null){
            return getPositionOfVar(initClass.get(className), varName);
        }
        else{
            for (Map.Entry<String, Pair> t : table.entrySet()){
                if(t.getKey().equals(varName)){
                    Integer pos = Integer.parseInt(t.getValue().getLeftField()) + 8;
                    return pos.toString();
                }
            }

            if(initClass.get(className) != null)
                return getPositionOfVar(initClass.get(className), varName);

        }
        return null;
    }

    //returns the type of the func
    String getFuncType(String className, String funcName){

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

        return null;
    }

    //returs the name of the baseclass and the corresponding funcType of a function which is declared in a baseclass
    //if the funcName does not exist in another baseclass return null
    String[] searchFuncInheritance(String className, String funcName){
        String baseClass = initClass.get(className);
        if(baseClass != null){
            if(funcsInClass.get(baseClass).containsKey(funcName)){
                String funcType = funcsInClass.get(baseClass).get(funcName).getLeftField();
                String funcArgs = funcsInClass.get(baseClass).get(funcName).getRightField();

                //if the function has atleast one argument
                StringBuffer argsbuffer = new StringBuffer("");
                if(!funcArgs.equals("")){
                    String[] argArray = funcArgs.split(",");

                    //for each argument find the corresponding type
                    for(String arg: argArray){
                        String[] splitArg= arg.trim().split("\\s+");
                        String argType;

                        if(splitArg.length == 2)
                            argType = splitArg[0];
                        else
                            argType = splitArg[0] + splitArg[1];
                        
                        argsbuffer.append("," + typeConverter(argType));
                    }
                }
                if(funcType.equals("int[]") || funcType.equals("boolean[]"))
                    return new String[] {baseClass, "( i8* (i8*" + argsbuffer};
                else
                    return new String[] {baseClass, "(" + typeConverter(funcType) + "(i8*" + argsbuffer};
            }
            if(initClass.get(baseClass) != null)
                return searchFuncInheritance(baseClass, funcName);
        }
        return null;
    }

    GenerateCode(FileWriter _writer, LinkedHashMap<String, String> _initClass, LinkedHashMap<String, LinkedHashMap<String, String>> _varsInClass,
                LinkedHashMap<String, LinkedHashMap<String, Pair>> _funcsInClass, LinkedHashMap<Pair,
                LinkedHashMap<String, String>> _varsInFuncs, LinkedHashMap<String,LinkedHashMap<String, Integer>> _vTables,
                LinkedHashMap<String,LinkedHashMap<String, Pair>> _offsetTable) throws Exception{
        
        //copy all the usefull data to our new visitor
        initClass = _initClass;
        varsInClass = _varsInClass;
        funcsInClass = _funcsInClass;
        varsInFuncs = _varsInFuncs;
        vTables = _vTables;
        offsetTable = _offsetTable;
        writer = _writer;

        //make the v_tables
        //iterate all classes
        for (Map.Entry<String, String> classes : initClass.entrySet()){
            String className = classes.getKey();

            //number of the functions that belong to the class
            int numOfFuncs = vTables.get(className).size();

            //declare the vtable of the class
            StringBuffer buffer = new StringBuffer("@." + className + "_vtable = global [" + numOfFuncs + " x i8*] [");

            //iterate all the functions of the class
            for (Map.Entry<String, Integer> vTable : vTables.get(className).entrySet()){

                //map the types of args to the correct LLVM type
                StringBuffer argsbuffer = new StringBuffer("");

                //continue with the checking only if the function is declared inside the class
                if(funcsInClass.get(className).containsKey(vTable.getKey())){
                    String argsInLine = funcsInClass.get(className).get(vTable.getKey()).getRightField();
                    String funcType = funcsInClass.get(className).get(vTable.getKey()).getLeftField();

                    if(funcType.equals("int[]") || funcType.equals("boolean[]"))
                        argsbuffer.append("( i8* (i8*");
                    else
                        argsbuffer.append("(" + typeConverter(funcType) + "(i8*");

                    //if the function has atleast one argument
                    if(!argsInLine.equals("")){
                        String[] argArray = argsInLine.split(",");

                        //for each argument find the corresponding type
                        for(String arg: argArray){
                            String[] splitArg= arg.trim().split("\\s+");
                            String argType;

                            if(splitArg.length == 2)
                                argType = splitArg[0];
                            else
                                argType = splitArg[0] + splitArg[1];
                            
                            argsbuffer.append("," + typeConverter(argType));
                        }
                    }
                }

                //here we check if the function has been overriden from the derived class
                String[] info = searchFuncInheritance(className, vTable.getKey());
                if(info != null && !funcsInClass.get(className).containsKey(vTable.getKey()))
                    buffer.append("\n\ti8* bitcast " + info[1] + ")* @" + info[0] + "." + vTable.getKey() + " to i8*),");
                else
                    buffer.append("\n\ti8* bitcast " + argsbuffer + ")* @" + className + "." + vTable.getKey() + " to i8*),");
            }

            //remove a not needed comma ","
            if(numOfFuncs > 0)
                writer.append(buffer.deleteCharAt(buffer.length()-1));
            else
                writer.append(buffer.toString());
            
            //close the vtable declarations
            writer.append("\n]\n\n");

        }

        //basic functions
        writer.append("declare i8* @calloc(i32, i32)\ndeclare i32 @printf(i8*, ...)\ndeclare void @exit(i32)\n\n");
        writer.append("@_cint = constant [4 x i8] c\"%d\\0a\\00\"\n");
        writer.append("@_cOOB = constant [15 x i8] c\"Out of bounds\\0a\\00\"\n");
        writer.append("@_cNSZ = constant [15 x i8] c\"Negative size\\0a\\00\"\n\n");
        writer.append("define void @print_int(i32 %i) {\n\t%_str = bitcast [4 x i8]* @_cint to i8*\n\tcall i32 (i8*, ...) @printf(i8* %_str, i32 %i)\n\tret void\n}\n\n");
        writer.append("define void @throw_oob() {\n\t%_str = bitcast [15 x i8]* @_cOOB to i8*\n\tcall i32 (i8*, ...) @printf(i8* %_str)\n\tcall void @exit(i32 1)\n\tret void\n}\n\n");
        writer.append("define void @throw_nsz() {\n\t%_str = bitcast [15 x i8]* @_cNSZ to i8*\n\tcall i32 (i8*, ...) @printf(i8* %_str)\n\tcall void @exit(i32 1)\n\tret void\n}\n\n");
        writer.append("%struct._BooleanArray = type { i32, i1* }\n\n");
        writer.append("%struct._IntegerArray = type { i32, i32* }\n\n");

    }

    /**
        * f1 -> Identifier()
        * f11 -> Identifier()
        * f15 -> ( Statement() )*
    */
    @Override
    public String visit(MainClass n, infoList argu) throws Exception {
        String _ret=null;

        writer.append("define i32 @main() {\n");

        //we write a alloca for each declared variable insdide the main function
        String className = n.f1.accept(this, null);

        infoList infolist = new infoList(className, "main");
        writeAllocas(infolist);
        
        n.f15.accept(this, infolist);
        
        //we khow that the main function returns 0
        writer.append("\tret i32 0\n}\n\n");
        return _ret;

    }

    /**
        * f0 -> ClassDeclaration()
        *       | ClassExtendsDeclaration()
    */
    @Override
    public String visit(TypeDeclaration n, infoList argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
        * f1 -> Identifier()
        * f4 -> ( MethodDeclaration() )*
    */
    @Override
    public String visit(ClassDeclaration n, infoList argu) throws Exception {
        String _ret=null;
        String className = n.f1.accept(this, argu);
        infoList infolist = new infoList(className, null);

        n.f4.accept(this, infolist);

        return _ret;
    }

    /**
    * f1 -> Identifier()
    * f6 -> ( MethodDeclaration() )*
    */
    @Override
    public String visit(ClassExtendsDeclaration n, infoList argu) throws Exception {
        String _ret=null;
        String className = n.f1.accept(this, argu);

        infoList infolist = new infoList(className, null);
        n.f6.accept(this, infolist);

        return _ret;
    }

    /**
        * f1 -> Type()
        * f2 -> Identifier()
        * f8 -> ( Statement() )*
        * f10 -> Expression()
    */
    @Override
    public String visit(MethodDeclaration n, infoList argu) throws Exception {
        String _ret=null;
        String funcType = n.f1.accept(this, argu);
        String funcName = n.f2.accept(this, argu);

        //write the definition of the function (type,name,args)
        //type
        funcType = typeConverter(funcType);
        
        if(funcType.equals("%struct._BooleanArray") || funcType.equals("%struct._IntegerArray"))
            funcType = "i8*";

        infoList infolist = new infoList(argu.getClassName(), funcName);
        //args
        String args = convertFuncArgs(getFuncArgs(infolist));
        writer.append("define " + funcType + " @" + infolist.getClassName() + "." + funcName + "(i8* %this" + args + ") {\n");

        writeAllocas(infolist);
        //we need to call store functionality for the arguments of the function
        storeArgs(args);
        
        n.f8.accept(this, infolist);

        //we set IdToReturn to true when we want to retreive the type and the register of the id
        //when idToLoad is set to true we khow that we are at the return statement
        infolist.setIdToReturn(true);
        String expr = n.f10.accept(this, infolist);
        String returnType = expr.split(" ")[0];
        String returnReg = expr.split(" ")[1];


        if(returnType.equals("%struct._BooleanArray") || returnType.equals("%struct._IntegerArray")){
            writer.append("\t%_" + rcounter + " = bitcast " + returnType + "* " + returnReg + " to i8*\n");
            writer.append("\tret i8* %_" + rcounter + "\n");
            rcounter++;
        }
        else{
            writer.append("\tret " + expr + "\n");
        }

        writer.append("}\n\n");
        return _ret;
    }

    /**
        * f0 -> ArrayType()
        *       | BooleanType()
        *       | IntegerType()
        *       | Identifier()
    */
    @Override
    public String visit(Type n, infoList argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
        * f0 -> BooleanArrayType()
        *       | IntegerArrayType()
    */
    @Override
    public String visit(ArrayType n, infoList argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    @Override
    public String visit(BooleanArrayType n, infoList argu) throws Exception {
        return "%struct._BooleanArray";
    }

    @Override
    public String visit(IntegerArrayType n, infoList argu) throws Exception {
        return "%struct._IntegerArray";
    }

    @Override
    public String visit(BooleanType n, infoList argu) throws Exception {
        return "i1";
    }

    @Override
    public String visit(IntegerType n, infoList argu) throws Exception {
        return "i32";
    }

    /**
        * f0 -> Block()
        *       | AssignmentStatement()
        *       | ArrayAssignmentStatement()
        *       | IfStatement()
        *       | WhileStatement()
        *       | PrintStatement()
    */

    //All statement functions(visitors) return null
    @Override
    public String visit(Statement n, infoList argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
        * f1 -> ( Statement() )*
    */
    @Override
    public String visit(Block n, infoList argu) throws Exception {
        String _ret=null;
        n.f1.accept(this, argu);
        return _ret;
    }

    /**
        * f0 -> Identifier()
        * f1 -> "="
        * f2 -> Expression()
        * f3 -> ";"
    */
    @Override
    public String visit(AssignmentStatement n, infoList argu) throws Exception {
        String _ret=null;
        infoList infolist = new infoList(argu);

        //we just want to khow to id
        //because we will need it to store the result of the expr to the reg of this id
        String id = n.f0.accept(this, infolist);

        //if the id returned is local inside the func we dont do anything besides getting its reg later
        //if the id returned is a var in the class we need to get it via the getelementptr
        //so we must check
        String check = getPositionOfVar(argu.getClassName(), id);
        String varType = getVarTypeLLVM(argu, id);
        String reg = getVarReg(argu, id);
        Integer temp;
        if(check != null){
            int pos = Integer.parseInt(check);
            writer.append("\t%_" + rcounter + " = getelementptr i8, i8* %this, i32 " + pos + "\n");

            temp = rcounter;
            rcounter++;
            writer.append("\t%_" + rcounter + " = bitcast i8* %_" + temp + " to " + varType + "*\n");

            reg = "%_" + rcounter;
            rcounter++;
        }

        //we want to get the reg and the type of this expr so we can store it
        infolist.setIdToReturn(true);
        String expr = n.f2.accept(this, infolist);
        String arrayExpr = expr.split(" ")[0] + " " + expr.split(" ")[1];

        //check whether the id is array or a simple var
        if(getVarTypeLLVM(infolist, id).equals("%struct._IntegerArray")){        
            writer.append("\t%_" + rcounter + " = getelementptr " + varType + ", " + varType + "* " + reg + ", i32 0, i32 1\n");
            temp = rcounter;
            rcounter++;
            writer.append("\tstore " + arrayExpr + ", i32** %_" + temp + "\n");

            //if split give us 4 lentgh that means that the expr was allocation so we have to store extra info
            if(expr.split(" ").length == 4){

                writer.append("\t%_" + rcounter + " = getelementptr " + varType + ", " + varType + "* " + reg + ", i32 0, i32 0\n");
                temp = rcounter;
                rcounter++;

                String sizeExpr = expr.split(" ")[2] + " " + expr.split(" ")[3];
                writer.append("\tstore " + sizeExpr + ", i32* %_" + temp + "\n");
            }
        }
        else if(getVarTypeLLVM(infolist, id).equals("%struct._BooleanArray")){
            writer.append("\t%_" + rcounter + " = getelementptr " + varType + ", " + varType + "* " + reg + ", i32 0, i32 1\n");
            temp = rcounter;
            rcounter++;
            writer.append("\tstore " + arrayExpr + ", i1** %_" + temp + "\n");

            if(expr.split(" ").length == 4){

                writer.append("\t%_" + rcounter + " = getelementptr " + varType + ", " + varType + "* " + reg + ", i32 0, i32 0\n");
                temp = rcounter;
                rcounter++;

                String sizeExpr = expr.split(" ")[2] + " " + expr.split(" ")[3];
                writer.append("\tstore " + sizeExpr + ", i32* %_" + temp + "\n");
            }
        }
        else{
            writer.append("\tstore " + arrayExpr + ", " + varType + "* " + reg + "\n\n");
        }
        return _ret;
    }

    /**
        * f0 -> Identifier()
        * f2 -> Expression()
        * f5 -> Expression()
    */
    @Override
    public String visit(ArrayAssignmentStatement n, infoList argu) throws Exception {
        String _ret=null;

        infoList infolist = new infoList(argu);
        //we want just the name of the id
        String id = n.f0.accept(this, infolist);

        //we find the type and the reg of the id with the helper functions
        String idType = getVarTypeLLVM(infolist, id);
        String idReg = getVarReg(infolist, id);

        //now we should check where the array is declared
        //if it is outside of the func we must take the offset and with getelementptr and change the previous reg
        String check = getPositionOfVar(argu.getClassName(), id);
        Integer temp;
        if(check != null){
            int offset = Integer.parseInt(check);
            writer.append("\t%_" + rcounter + " = getelementptr i8, i8* %this, i32 " + offset + "\n");

            temp = rcounter;
            rcounter++;
            writer.append("\t%_" + rcounter + " = bitcast i8* %_" + temp + " to " + idType + "*\n");

            //we update the register
            idReg = "%_" + rcounter;
            rcounter++;
        }

        //we want the registers of the following exprs
        infolist.setIdToReturn(true);
        String pos = n.f2.accept(this, infolist);

        infolist.setIdToReturn(true);
        String expr = n.f5.accept(this, infolist);

        //here we take the reg of the array and the size
        String reg = getArray(idType, idReg);
        String size = getSizeOfArray(idType, idReg);
        //check for oob exception
        checkOob(pos, size);

        if(idType.equals("%struct._IntegerArray")){
            writer.append("\t%_" + rcounter + " = getelementptr i32, i32* " + reg + ", " + pos + "\n");
            temp = rcounter;
            rcounter++;

            writer.append("\tstore " + expr + ", i32* %_" + temp + "\n");
        }
        else{
            writer.append("\t%_" + rcounter + " = getelementptr i1, i1* " + reg + ", " + pos + "\n");
            temp = rcounter;
            rcounter++;

            writer.append("\tstore " + expr + ", i1* %_" + temp + "\n");
        }

        return _ret;
    }

    /**
        * f2 -> Expression()
        * f4 -> Statement()
        * f6 -> Statement()
    */
    @Override
    public String visit(IfStatement n, infoList argu) throws Exception {
        String _ret=null;

        infoList list = new infoList(argu);

        list.setIdToReturn(true);
        String expr = n.f2.accept(this, list);
        String exprReg = expr.split(" ")[1];

        String ifThen = createIfThenLabel();
        String ifElse = createIfElseLabel();
        writer.append("\tbr i1 " + exprReg + ", label " + ifThen + ", label " + ifElse + "\n");
        writer.append("\t" + ifElse.replace("%", "") + ":\n");
        //else
        n.f6.accept(this, list);
        String ifEnd = createIfEndLabel();
        writer.append("\tbr label " + ifEnd + "\n");
        writer.append("\t" + ifThen.replace("%", "") + ":\n");
        //if
        n.f4.accept(this, list);
        writer.append("\tbr label " + ifEnd + "\n");
        writer.append("\t" + ifEnd.replace("%", "") + ":\n");

        return _ret;
    }

    /**
        * f2 -> Expression()
        * f4 -> Statement()
    */
    @Override
    public String visit(WhileStatement n, infoList argu) throws Exception {
        String _ret=null;

        infoList list = new infoList(argu);

        String whileStart = createWhileStart();
        writer.append("\tbr label " + whileStart + "\n");
        writer.append("\t" + whileStart.replace("%", "") + ":\n");

        list.setIdToReturn(true);
        String expr = n.f2.accept(this, list);
        String exprReg = expr.split(" ")[1];

        String whileBody = createWhileBody();
        String whileExit = createWhileExit();
        writer.append("\tbr i1 " + exprReg + ", label " + whileBody + ", label " + whileExit + "\n");
        writer.append("\t" + whileBody.replace("%", "") + ":\n");
        n.f4.accept(this, list);
        writer.append("\tbr label " + whileStart + "\n");
        writer.append("\t" + whileExit.replace("%", "") + ":\n");

        return _ret;
    }

    /**
        * f2 -> Expression()
    */
    @Override
    public String visit(PrintStatement n, infoList argu) throws Exception {
        String _ret=null;
        infoList list = new infoList(argu);

        list.setIdToReturn(true);
        String expr = n.f2.accept(this, list);

        String varType = expr.split(" ")[0];
        //our program supports also to print the booleans as ints
        //here we convert a boolean input to int
        if(varType.equals("i1")){
            writer.append("\t%_" + rcounter + " = zext " + expr + " to i32\n");
            writer.append("\tcall void (i32) @print_int(i32 %_" + rcounter + ")\n");
        }
        else{
            writer.append("\tcall void (i32) @print_int(" + expr + ")\n");
        }

        rcounter++;
        return _ret;
    }

    /**
        * f0 -> AndExpression()
        *       | CompareExpression()
        *       | PlusExpression()
        *       | MinusExpression()
        *       | TimesExpression()
        *       | ArrayLookup()
        *       | ArrayLength()
        *       | MessageSend()
        *       | Clause()
    */
    @Override
    public String visit(Expression n, infoList argu) throws Exception {
        return n.f0.accept(this, argu);
    }


    /**
     * f0 -> Clause()
    * f1 -> "&&"
    * f2 -> Clause()
    */
    @Override
    public String visit(AndExpression n, infoList argu) throws Exception {
        argu.setIdToReturn(true);

        //clause will have the form of "type %name"
        String clauseL = n.f0.accept(this, argu);

        //so we use split to get the info we need
        String type = clauseL.split(" ")[0];
        String name = clauseL.split(" ")[1];

        String ifLeftFalse = createExprResLabel();
        String ifLeftTrue = createExprResLabel();
        writer.append("\tbr " + type + " " + name + ", label " + ifLeftTrue + ", label " + ifLeftFalse + "\n\n");
        writer.append("\t" + ifLeftFalse.replace("%", "") + ":\n");

        String exit1 = createExprResLabel();
        writer.append("\tbr label " + exit1 + "\n\n");
        writer.append("\t" + ifLeftTrue.replace("%", "") + ":\n");

        argu.setIdToReturn(true);
        String clauseR = n.f2.accept(this, argu);
        type = clauseR.split(" ")[0];
        name = clauseR.split(" ")[1];

        String exit2 = createExprResLabel();
        writer.append("\tbr label " + exit2 + "\n\n");
        writer.append("\t" + exit2.replace("%", "") + ":\n");
        writer.append("\tbr label " + exit1 + "\n\n");

        writer.append("\t" + exit1.replace("%", "") + ":\n");
        writer.append("\t%_" + rcounter + " = phi i1 [ 0, " + ifLeftFalse + " ], [ " + name +  ", " + exit2 + " ]\n");
        
        //temp holds the result of the if expr
        Integer temp = rcounter;
        rcounter++;
        
        //this visitor will always return boolean i1
        return "i1 %_" + temp.toString();
    }


    /**
        * f0 -> PrimaryExpression()
        * f2 -> PrimaryExpression()
    */
    @Override
    public String visit(CompareExpression n, infoList argu) throws Exception {
        argu.setIdToReturn(true);
        String primaryL = n.f0.accept(this, argu);

        argu.setIdToReturn(true);
        String primaryR = n.f2.accept(this, argu);

        String prType = primaryL.split(" ")[0];
        String primaryLReg = primaryL.split(" ")[1];
        String primaryRReg = primaryR.split(" ")[1];

        writer.append("\t%_" + rcounter + " = icmp slt " + prType + " " + primaryLReg + ", " + primaryRReg + "\n");
        Integer temp = rcounter;
        rcounter++;

        return "i1 %_" + temp;
    }

    /**
        * f0 -> PrimaryExpression()
        * f2 -> PrimaryExpression()
    */
    @Override
    public String visit(PlusExpression n, infoList argu) throws Exception {

        //we want to khow the registers and their type
        argu.setIdToReturn(true);
        String idl = n.f0.accept(this, argu);

        argu.setIdToReturn(true);
        String idr = n.f2.accept(this, argu);

        //idl and idr has the form -> id %reg (ex. i32 %_1)
        //so if we split them by " " to get the info we need
        String type = idl.split(" ")[0];
        String idlReg = idl.split(" ")[1];
        String idrReg = idr.split(" ")[1];

        //it does not matter if we are in a ret expression of a basic expression
        //we always need to send back the type and the register of the plus expr
        Integer temp = rcounter;
        writer.append("\t%_" + rcounter + " = add " + type + " " +  idlReg + ", " + idrReg + "\n");
        rcounter++;

        return type + " " + "%_" + temp.toString();
    }

    /**
        * f0 -> PrimaryExpression()
        * f2 -> PrimaryExpression()
    */
    @Override
    public String visit(MinusExpression n, infoList argu) throws Exception {
        //we want to khow the registers and their type
        argu.setIdToReturn(true);
        String idl = n.f0.accept(this, argu);

        argu.setIdToReturn(true);
        String idr = n.f2.accept(this, argu);

        //idl and idr has the form -> id %reg (ex. i32 %_1)
        //so if we split them by " " to get the info we need
        String type = idl.split(" ")[0];
        String idlReg = idl.split(" ")[1];
        String idrReg = idr.split(" ")[1];

        //it does not matter if we are in a ret expression of a basic expression
        //we always need to send back the type and the register of the plus expr
        Integer temp = rcounter;
        writer.append("\t%_" + rcounter + " = sub " + type + " " +  idlReg + ", " + idrReg + "\n");
        rcounter++;

        return type + " " + "%_" + temp.toString();
    }

    /**
        * f0 -> PrimaryExpression()
        * f2 -> PrimaryExpression()
    */
    @Override
    public String visit(TimesExpression n, infoList argu) throws Exception {

        //we want to khow the registers and their type
        argu.setIdToReturn(true);
        String idl = n.f0.accept(this, argu);

        argu.setIdToReturn(true);
        String idr = n.f2.accept(this, argu);

        //idl and idr has the form -> id %reg (ex. i32 %_1)
        //so if we split them by " " to get the info we need
        String type = idl.split(" ")[0];
        String idlReg = idl.split(" ")[1];
        String idrReg = idr.split(" ")[1];

        //it does not matter if we are in a ret expression of a basic expression
        //we always need to send back the type and the register of the plus expr
        Integer temp = rcounter;
        writer.append("\t%_" + rcounter + " = mul " + type + " " +  idlReg + ", " + idrReg + "\n");
        rcounter++;

        return type + " " + "%_" + temp.toString();

    }

    /**
        * f0 -> PrimaryExpression()
        * f2 -> PrimaryExpression()
    */
    @Override
    public String visit(ArrayLookup n, infoList argu) throws Exception {
        infoList list = new infoList(argu);

        list.setIdToReturn(true);
        String id = n.f0.accept(this, list);

        //we find the type and the reg of the id for the helper functions
        String idType = id.split(" ")[0];
        String idReg = id.split(" ")[1];

        list.setIdToReturn(true);
        String pos = n.f2.accept(this, list);

        Integer temp;
        String reg = getArray(idType, idReg);
        String size = getSizeOfArray(idType, idReg);
        checkOob(pos, size);

        if(idType.equals("%struct._IntegerArray")){
            writer.append("\t%_" + rcounter + " = getelementptr i32, i32* " + reg + ", " + pos + "\n");
            temp = rcounter;
            rcounter++;
    
            writer.append("\t%_" + rcounter + " = load i32, i32* %_" + temp + "\n");
            temp = rcounter;
            rcounter++;

            return "i32 %_" + temp;
        }
        else{
            writer.append("\t%_" + rcounter + " = getelementptr i1, i1* " + reg + ", " + pos + "\n");
            temp = rcounter;
            rcounter++;
    
            writer.append("\t%_" + rcounter + " = load i1, i1* %_" + temp + "\n");
            temp = rcounter;
            rcounter++;

            return "i1 %_" + temp;
        }
    }

    /**
        * f0 -> PrimaryExpression()
    */
    @Override
    public String visit(ArrayLength n, infoList argu) throws Exception {
        infoList list = new infoList(argu);
        
        list.setIdToReturn(true);
        String primary = n.f0.accept(this, list);

        String type = primary.split(" ")[0];
        String reg = primary.split(" ")[1];

        return "i32 " + getSizeOfArray(type, reg);
    }

    /**
    * f0 -> PrimaryExpression()
    * f1 -> "."
    * f2 -> Identifier()
    * f3 -> "("
    * f4 -> ( ExpressionList() )?
    * f5 -> ")"
    */
    @Override
    public String visit(MessageSend n, infoList argu) throws Exception {
        infoList list = new infoList(argu);
        
        list.setIdToReturn(true);
        String primary = n.f0.accept(this, list);

        String prReg = primary.split(" ")[1];
        String prType = primary.split(" ")[0];
        Integer temp;

        writer.append("\t%_" + rcounter + " = bitcast i8* " + prReg + " to " + prType + "**\n");
        temp = rcounter;
        rcounter++;

        writer.append("\t%_" + rcounter + " = load i8**, i8*** %_" + temp + "\n");
        temp = rcounter;
        rcounter++;

        //here we want just the name of the func
        list.setIdToReturn(false);
        String funcName = n.f2.accept(this, list);

        String className = getVarType(list, n.f0.accept(this, list));
        if(className == null)
            className = regsTypes.get(prReg);
        
        //get the return type of the function   
        String funcType = typeConverter(getFuncType(className, funcName));

        //we find the offset of the func in the vtable
        //we divide by 8 to get the correct pos in the vtable
        Integer offset = vTables.get(className).get(funcName)/8;

        //get the argument list in form of (, type %.x, type %.y, ...)
        String args = convertFuncArgs(getFuncArgs(new infoList(className, funcName)));
        String argsSplit[] = args.replace(",", "").trim().split("\\s+");

        //we want only the types of the args so we modified them
        String typesOnly = "i8*";
        for(String a: argsSplit){
            if(a.equals("%struct._BooleanArray") || a.equals("%struct._IntegerArray"))
                typesOnly += ", " + a + "*";
            else if(a.contains("%") || a.equals(""))
                continue;
            else
                typesOnly += ", " + a;
        }

        writer.append("\t%_" + rcounter + " = getelementptr i8*, i8** %_" + temp + ", i32 " + offset + "\n");
        temp = rcounter;
        rcounter++;

        writer.append("\t%_" + rcounter + " = load i8*, i8** %_" + temp + "\n");
        temp = rcounter;
        rcounter++;

        writer.append("\t%_" + rcounter + " = bitcast i8* %_" + temp + " to " + funcType + " (" + typesOnly + ")*\n");;
        temp = rcounter;
        rcounter++;

        list.setIdToReturn(true);
        String listargs = n.f4.accept(this, list);
        String finalList = "";
        if(listargs != null){
            String splitArgs[] = listargs.split(",");
            for(String a: splitArgs){
                String[] argTemp = a.split(" ");
                if(argTemp[0].equals("%struct._BooleanArray") || argTemp[0].equals("%struct._IntegerArray"))
                    finalList += ", " + argTemp[0] + "* " + argTemp[1];
                else
                    finalList += ", " + argTemp[0] + " " + argTemp[1];
            }
        }
        else
            finalList = "";
        
        writer.append("\t%_" + rcounter + " = call " + funcType + " %_" + temp + "(i8* " + prReg + finalList + ")\n");
        temp = rcounter;
        rcounter++;

        regsTypes.put("%_" + temp, className);

        return funcType + " %_" + temp;
    }

    /**
        * f0 -> Expression()
        * f1 -> ExpressionTail()
    */
    @Override
    public String visit(ExpressionList n, infoList argu) throws Exception {
        String expr = n.f0.accept(this, argu);
        String exprTail = n.f1.accept(this, argu);
        if(exprTail == null)
            return expr;
        else
            return expr + " " + exprTail;
    
    }

    /**
        * f0 -> ( ExpressionTerm() )*
    */
    @Override
    public String visit(ExpressionTail n, infoList argu) throws Exception {
        String ret = "";
        for (Node node: n.f0.nodes)
            ret += " " + node.accept(this, argu);

        return ret;   
    }

    /**
        * f1 -> Expression()
    */
    @Override
    public String visit(ExpressionTerm n, infoList argu) throws Exception {
        argu.setIdToReturn(true);
        return "," + n.f1.accept(this, argu);
    }

    /**
        * f0 -> NotExpression()
        *       | PrimaryExpression()
    */
    @Override
    public String visit(Clause n, infoList argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
        * f0 -> IntegerLiteral()
        *       | TrueLiteral()
        *       | FalseLiteral()
        *       | Identifier()
        *       | ThisExpression()
        *       | ArrayAllocationExpression()
        *       | AllocationExpression()
        *       | BracketExpression()
    */
    @Override
    public String visit(PrimaryExpression n, infoList argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    @Override
    public String visit(IntegerLiteral n, infoList argu) throws Exception {
        return "i32 " + n.f0.toString();
    }

    @Override
    public String visit(TrueLiteral n, infoList argu) throws Exception {
        return "i1 1";
    }

    @Override
    public String visit(FalseLiteral n, infoList argu) throws Exception {
        return "i1 0";
    }

    @Override
    public String visit(Identifier n,  infoList argu) throws Exception {
        String expr = n.f0.toString();
        //we check if we got the info that we need to load from memory the Identifier   
        if(argu != null && argu.getIdToReturn()){
            argu.setIdToReturn(false);

            //now we must check if the var we are looking is local inside the function
            //or it has been declared in some class

            //if its declared in the class we must use getelementptr
            String check = getPositionOfVar(argu.getClassName(), expr);
            int temp;
            String varType = getVarTypeLLVM(argu, expr);
            if(check != null){
                int pos = Integer.parseInt(check);
                writer.append("\t%_" + rcounter + " = getelementptr i8, i8* %this, i32 " + pos + "\n");

                temp = rcounter;
                rcounter++;
                writer.append("\t%_" + rcounter + " = bitcast i8* %_" + temp + " to " + varType + "*\n");

                temp = rcounter;
                rcounter++;
                
                //if the vartype is int/bool array we dont want to load them just the ptr from getelementptr
                if(varType.equals("%struct._IntegerArray") || varType.equals("%struct._BooleanArray")){
                    return varType + " %_" + temp;
                }
                else{
                    writer.append("\t%_" + rcounter + " = load " + varType + ", " + varType + "* %_" + temp + "\n");
                }
            }
            //else it is a local variable declared inside the function and has a register already
            else{
                String reg = getVarReg(argu, expr);   
                //if the vartype is int/bool array we dont want to load them just the register
                if(varType.equals("%struct._IntegerArray") || varType.equals("%struct._BooleanArray")){
                    return varType + " " + reg;
                }
                //else we just load it to a new register
                else{
                    //we must update the register cause we used the old one
                    writer.append("\t%_" + rcounter + " = load " + varType + ", " + varType + "* " + reg + "\n");
                    String newReg = "%_" + rcounter.toString();
                    updateVarReg(argu, expr, newReg);
                }
            }

            temp = rcounter;
            rcounter++;
            //in this case we return type of the expr and the register, needed for the ret expression
            return varType + " %_" + temp;

        }
        return expr;
    }

    @Override
    public String visit(ThisExpression n, infoList argu) throws Exception {
        return "i8* %this";
    }

    /**
        * f0 -> BooleanArrayAllocationExpression()
        *       | IntegerArrayAllocationExpression()
    */
    @Override
    public String visit(ArrayAllocationExpression n, infoList argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
        * f3 -> Expression()
    */
    public String visit(BooleanArrayAllocationExpression n, infoList argu) throws Exception {
        infoList list = new infoList(argu);

        list.setIdToReturn(true);
        String expr = n.f3.accept(this, list);

        Integer temp;
        //we must chech that the expr is not negative
        writer.append("\t%_" + rcounter + " = icmp sge " + expr + ", 0\n");
        temp = rcounter;
        rcounter++;

        String nszOK = createcNszOk();
        String nszError = createcNszError();

        writer.append("\tbr i1 %_" + temp + ", label " + nszOK + ", label " + nszError + "\n");
        writer.append("\t" + nszError.replace("%", "") + ":\n");
        writer.append("\tcall void @throw_nsz()\n");
        writer.append("\tbr label " + nszOK + "\n");
        writer.append("\t" + nszOK.replace("%", "") + ":\n");
        writer.append("\t%_" + rcounter + " = call i8* @calloc(" + expr + ", i32 1)\n");
        temp = rcounter;
        rcounter++;
        writer.append("\t%_" + rcounter + " = bitcast i8* %_" + temp + " to i1*\n");
        temp = rcounter;
        rcounter++;

        return "i1* %_" + temp + " " + expr;
    }

    /**
        * f3 -> Expression()
    */
    @Override
    public String visit(IntegerArrayAllocationExpression n, infoList argu) throws Exception {
        infoList list = new infoList(argu);

        list.setIdToReturn(true);
        String expr = n.f3.accept(this, list);

        Integer temp;
        //we must chech that the expr is not negative
        writer.append("\t%_" + rcounter + " = icmp sge " + expr + ", 0\n");
        temp = rcounter;
        rcounter++;

        String nszOK = createcNszOk();
        String nszError = createcNszError();

        writer.append("\tbr i1 %_" + temp + ", label " + nszOK + ", label " + nszError + "\n");
        writer.append("\t" + nszError.replace("%", "") + ":\n");
        writer.append("\tcall void @throw_nsz()\n");
        writer.append("\tbr label " + nszOK + "\n");
        writer.append("\t" + nszOK.replace("%", "") + ":\n");
        writer.append("\t%_" + rcounter + " = call i8* @calloc(" + expr + ", i32 4)\n");
        temp = rcounter;
        rcounter++;
        writer.append("\t%_" + rcounter + " = bitcast i8* %_" + temp + " to i32*\n");
        temp = rcounter;
        rcounter++;

        return "i32* %_" + temp + " " + expr;
    }

    @Override
    public String visit(AllocationExpression n, infoList argu) throws Exception {
        //we just want the name of the class constructor
        argu.setIdToReturn(false);
        String calledClass = n.f1.accept(this, argu);
        String size = sizeOfClass(calledClass);

        writer.append("\t%_" + rcounter + " = call i8* @calloc(i32 1, i32 " + size + ")\n");
        int temp1 = rcounter;
        rcounter++;
        writer.append("\t%_" + rcounter + " = bitcast i8* %_" + temp1 + " to i8***\n");

        int temp2 = rcounter;
        rcounter++;
        //number of the functions that belong to the class
        int numOfFuncs = vTables.get(calledClass).size();
        writer.append("\t%_" + rcounter + " = getelementptr [" + numOfFuncs + " x i8*], [" + numOfFuncs + " x i8*]* @." + calledClass +"_vtable, i32 0, i32 0\n");
        writer.append("\tstore i8** %_" + rcounter + ", i8*** %_" + temp2 + "\n");
        rcounter++;

        regsTypes.put("%_" + temp1, calledClass);
        return "i8* %_" + temp1;
    }

    /**
        * f1 -> Clause()
    */
    @Override
    public String visit(NotExpression n, infoList argu) throws Exception {
        argu.setIdToReturn(true);
        String expr =  n.f1.accept(this, argu);
        String exprReg = expr.split(" ")[1];

        //we can use the xor command to change 0 to 1 and 1 to 0
        //that way we can achieve the not functionality
        writer.append("\t%_" + rcounter + " = xor i1 1, " + exprReg + "\n");
        Integer temp = rcounter;
        rcounter++;

        return "i1 %_" + temp;
    }

    /**
        * f1 -> Expression()
    */
    @Override
    public String visit(BracketExpression n, infoList argu) throws Exception {
        return n.f1.accept(this, argu);
    }
}
