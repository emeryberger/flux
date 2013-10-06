package edu.umass.cs.flux;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * Generates explicitly threaded program for static analysis
 * @author Vitaliy Lvin (vlvin@cs.umass.edu)
 */
public class DummyGeneratorC extends AbstractGenerator {
	protected static final String M_DUMMY_THREAD_C = "mDummyThread.c";
	protected static final String M_IMPL_C         = "mImpl.c";
	protected static final String M_PROTO_H        = "mProto.h";
	protected static final String M_IMPL_H         = "mImpl.h";
	protected static final String M_STRUCT_H       = "mStruct.h";
	protected static final String ERR_NODE_NOT_FOUND = 
		"Node %s is undeclared!\n";
	
    protected VirtualDirectory out;
	
	protected Program program;
	
	protected Set<TaskDeclaration> declaredTasks = new HashSet<TaskDeclaration>();
	protected Set<String> declaredTypes = new HashSet<String>();
	protected Map<String, String> typeAliases = new HashMap<String, String>();
	protected Set<TaskDeclaration> declaredInputs = new HashSet<TaskDeclaration>();
	protected Set<TaskDeclaration> declaredOutputs = new HashSet<TaskDeclaration>();
    
	/**
	 * Generic declarations that go in the begging of file
	 */
	protected void prologue() {
		//main file
		out.getWriter(M_DUMMY_THREAD_C).println("#include <pthread.h>");
		out.getWriter(M_DUMMY_THREAD_C).println("#include \"" + M_IMPL_H + 
				"\"");
		out.getWriter(M_DUMMY_THREAD_C).println("#include \"" + M_PROTO_H + 
				"\"\n");
		out.getWriter(M_DUMMY_THREAD_C).println("bool running;");
		out.getWriter(M_DUMMY_THREAD_C).println("int get_new_session(void) {");
		out.getWriter(M_DUMMY_THREAD_C).println(
				"static int sessionCount = 0;");
		out.getWriter(M_DUMMY_THREAD_C).println("return sessionCount++;\n}");
		out.getWriter(M_DUMMY_THREAD_C).println("void *runAndDelete(void *a) {\n " +
			  "pair *arg = (pair *)a;\n" +
			  "function_closure *closure = (function_closure*)arg->second;\n" +
			  "closure->runInt(closure->data, closure->ddata);\n" +
			  "free(closure->data);\nfree(closure->ddata);\nfree(arg->second);\n" +
			  "free(arg->first);\nfree(arg);\n}");
		
		//header file
		out.getWriter(M_PROTO_H).println("#ifndef __M_PROTO__");
		out.getWriter(M_PROTO_H).println("#define __M_PROTO__");
		
		//implementation header file
		out.getWriter(M_IMPL_H).println("#ifndef __M_IMPL__");
		out.getWriter(M_IMPL_H).println("#define __M_IMPL__");
		out.getWriter(M_IMPL_H).println("#include \"" + M_STRUCT_H + "\"");
		out.getWriter(M_IMPL_H).println("void init(int argc, char **argv);");
		//implementation C file
		out.getWriter(M_IMPL_C).println("#include \"" + M_IMPL_H + "\"\n");
		out.getWriter(M_IMPL_C).println(
				"void init(int argc, char **argv) {\n}\n");
		//structs
		out.getWriter(M_STRUCT_H).println("#ifndef __M_STRUCTS__");
		out.getWriter(M_STRUCT_H).println("#define __M_STRUCTS__");
		out.getWriter(M_STRUCT_H).println("\ntypedef int bool;\n");
		out.getWriter(M_STRUCT_H).println("#define FALSE 0");
		out.getWriter(M_STRUCT_H).println("#define TRUE  1\n");
		out.getWriter(M_STRUCT_H).println("typedef struct pair {");
		out.getWriter(M_STRUCT_H).println(
				"  void *first;\n  void *second;\n} pair;\n");
		out.getWriter(M_STRUCT_H).println(
				"typedef void* (*run_func) (void*, void*);\n");
		out.getWriter(M_STRUCT_H).println("typedef struct function_closure {");
		out.getWriter(M_STRUCT_H).println(
				"  void *data;\n  void *ddata;\n  run_func runInt;");
		out.getWriter(M_STRUCT_H).println("  run_func run;\n} function_closure;\n");
		//out.getWriter(M_STRUCT_H).println("typedef struct flux_session {");
		//out.getWriter(M_STRUCT_H).println("  int session_id;\n} flux_session;\n");
	}
	
	/**
	 * 
	 * Generic things that go in the end of the file
	 */
	protected void epilogue() {
		out.getWriter(M_IMPL_H).println("#endif //__M_IMPL__");
		out.getWriter(M_STRUCT_H).println("#endif //__M_STRUCTS__");
		out.getWriter(M_PROTO_H).println("#endif// __M_PROTO__");
	}
	
	
	protected String getInputName(TaskDeclaration task) {
		if (task.getInputs().size() == 0)
			return null;
		
		return task.getName() + "_in";
	}
	
	protected String getOutputName(TaskDeclaration task) {
		if (task.getOutputs().size() == 0)
			return null;
		
		return task.getName() + "_out";
	}
	
	protected String generateStruct(Vector<Argument> fields) {
		if (fields.size() > 0) {
			//generate name (maybe need to sort?)
			String name = "";
			for (int i = 0; i < fields.size(); i++) {
				name += fields.get(i).getType() + "_";
			}
			//System.out.println(name);
			name = name.replace("*", "_ptr");
			//System.out.println(name);
			//don't declare twice
			if (declaredTypes.contains(name)) 
				return name;
			
			declaredTypes.add(name);
			
			//create input argument struct
			out.getWriter(M_STRUCT_H).println("typedef struct " + name + " {");
			out.getWriter(M_STRUCT_H).println("int _mSession;");
			for (int i = 0; i < fields.size(); i++) {
				out.getWriter(M_STRUCT_H).println("  " +
					fields.get(i).getType() + " " + 
					fields.get(i).getName() + ";");
			}
			
			out.getWriter(M_STRUCT_H).println("} " + name + ";\n");
			
			return name;
		}
		return null;
	}
	
	/**
	 * Generates input structure for user-implemented task
	 * @param task
	 */
	protected void generateInputParameterType(TaskDeclaration task) {
		
		if (declaredInputs.contains(task)) return;
		
		declaredInputs.add(task);
		
		String name = generateStruct(task.getInputs());
	
		if (name == null) return;
		
		out.getWriter(M_STRUCT_H).println("typedef " + name + " " + 
				getInputName(task) + ";");
		typeAliases.put(getInputName(task), name);
	
	}
	
	/**
	 * Generates output structure for user-implemented task
	 * @param task
	 */
	protected void generateOutputParameterType(TaskDeclaration task) {
		
		if (declaredOutputs.contains(task)) return;
		
		declaredOutputs.add(task);
		
		String name = generateStruct(task.getOutputs());
		
		if (name == null) return;
		
		out.getWriter(M_STRUCT_H).println("typedef " + name + " " + 
				getOutputName(task) + ";");
	
		typeAliases.put(getOutputName(task), name);
	}
	
	/**
	 * Generates stub and prototype for a node that is to be implemented by 
	 * the user
	 * @param task node to generate prototype for
	 */
	protected void generateExternalTaskPrototype(TaskDeclaration task) {
		
		//generate input and output parameter structures, and get their names 
		generateInputParameterType(task);
		generateOutputParameterType(task);
		
		AtomicDeclaration a = program.getAtomicDeclaration(task.getName());
		
		if (a != null) {
			out.getWriter(M_IMPL_H).print("__attribute__((atomic)) ");
			out.getWriter(M_IMPL_C).print("__attribute__((atomic)) ");	
		}
		out.getWriter(M_IMPL_H).print("int " + task.getName() + "(");
		out.getWriter(M_IMPL_C).print("int " + task.getName() + "(");
		//there may not be an input argument
		if (task.getInputs().size() > 0) {
			out.getWriter(M_IMPL_H).print(getInputName(task) + " *in");
			out.getWriter(M_IMPL_C).print(getInputName(task) + " *in");
		}
		//there may npt be an output argument
		if (task.getOutputs().size() > 0) {
			if (task.getInputs().size() > 0) {
				out.getWriter(M_IMPL_H).print(",");
				out.getWriter(M_IMPL_C).print(",");
			}
			out.getWriter(M_IMPL_H).print(getOutputName(task)+ " *out");
			out.getWriter(M_IMPL_C).print(getOutputName(task)+ " *out");
		}
		
		out.getWriter(M_IMPL_H).print(");\n");
		out.getWriter(M_IMPL_C).print(") {\n}\n");
		
		//error handler 
		if (task.getErrorHandler() != null) {
			//prototype 
    		out.getWriter(M_IMPL_H).print("void " + task.getErrorHandler() + 
    				"(");
    		out.getWriter(M_IMPL_C).print("void " + task.getErrorHandler() + 
    				"(");
    		//there may not be an input??? is that true?
    		if (task.getInputs().size() > 0) {
    			out.getWriter(M_IMPL_H).print(getInputName(task) + " *in, ");
    			out.getWriter(M_IMPL_C).print(getInputName(task) + " *in, ");
    		}
    		
    		out.getWriter(M_IMPL_H).println("int err);\n");
    		out.getWriter(M_IMPL_C).println("int err) {\n}\n");
		}
	}
	
    /**
     * Generates a prototype for internal (not implemented by the user) node
     * @param task Node to generate prototype for
     */
    protected void generateInternalTaskPrototype(TaskDeclaration task) {
	//String inName, String outName) {
		String inName = getInputName(task);
		String outName = getOutputName(task);

		AtomicDeclaration a = program.getAtomicDeclaration(task.getName());
		
		if (a != null) {
			out.getWriter(M_PROTO_H).print("__attribute__((atomic)) ");
			out.getWriter(M_DUMMY_THREAD_C).print("__attribute__((atomic)) ");	
		}
		//prototype
		out.getWriter(M_PROTO_H).print("int " + task.getName() + "(");
		out.getWriter(M_DUMMY_THREAD_C).print("int " + task.getName() + "(");
		
		if (inName != null) {
			out.getWriter(M_DUMMY_THREAD_C).print(inName + " *in");
			out.getWriter(M_PROTO_H).print(inName + " *in");
		}
		
		if (outName != null) { 
			if (inName != null) {
				out.getWriter(M_DUMMY_THREAD_C).print(", ");
				out.getWriter(M_PROTO_H).print(", ");
			}
			out.getWriter(M_DUMMY_THREAD_C).print(outName + " *out");
			out.getWriter(M_PROTO_H).print(outName + " *out");
		}
		
		out.getWriter(M_PROTO_H).println(");");
		out.getWriter(M_DUMMY_THREAD_C).println(") {");
	}
	
	/**
	 * Generates an (unconditional) call to a node inside of compound node
	 * @param task node to call
	 * @param in input argument
	 * @param cont true if the execution is to be continued on error, false to 
	 * be interrruped by a return statement
	 * @return output argument
	 */
	private void generateSimpleCall(TaskDeclaration task, String in, 
			String outp, boolean cont) {
		
		//call
		out.getWriter(M_DUMMY_THREAD_C).print("err = " + task.getName() + "(");
		
		if (in != null)		
			out.getWriter(M_DUMMY_THREAD_C).print(in); 
		
		if (outp != null) {
			if (in != null)
				out.getWriter(M_DUMMY_THREAD_C).print(", ");
			out.getWriter(M_DUMMY_THREAD_C).print(outp);
		}
		out.getWriter(M_DUMMY_THREAD_C).println(");");
		//check for error
		out.getWriter(M_DUMMY_THREAD_C).println("if (err != 0) {");
		
		String ehandler = task.getErrorHandler();
		
		if (ehandler != null) {
			//call error handler
			out.getWriter(M_DUMMY_THREAD_C).print(ehandler + "(");
			
			if (in != null) {
				out.getWriter(M_DUMMY_THREAD_C).print("(" + getInputName(task)+
						"*)" + in + ", ");
			}
			
			out.getWriter(M_DUMMY_THREAD_C).println("err);");	
		}
		
		if (cont)
			out.getWriter(M_DUMMY_THREAD_C).println("continue;");
		else	
			out.getWriter(M_DUMMY_THREAD_C).println("return -1;");
		
		out.getWriter(M_DUMMY_THREAD_C).println("}");
	}
	
	/**
	 * Creates the generator
	 * @param root The root directory to output into
	 * @param p The program
	 **/
    public DummyGeneratorC(String root, Program p) {
	out = new VirtualDirectory(root);	
    	
    	program = p;
	}
	
	protected void generateTask(TaskDeclaration task) {
		if (declaredTasks.contains(task)) return;
		
		declaredTasks.add(task);
		
		FlowStatement f = program.getFlow(task.getName());
		
		if (f == null) {
			generateExternalTaskPrototype(task);
		}
		else {
			generateFromFlowStatement(f);
		}
	}
	
	/**
	 * Generates implementation of a simple compound node with no branches
	 * @param sf object describing the flow statement
	 */
	protected void generateFromUntypedFlow(SimpleFlowStatement sf) {
		TaskDeclaration compoundNode = program.getTask(sf.getAssignee());
		Vector<String> args = sf.getArguments();
		
		for (Iterator<String> it = args.iterator(); it.hasNext();) {
			generateTask(program.getTask(it.next()));
		}
	
		//generate prototypes for the new node
		generateInternalTaskPrototype(compoundNode);
		generateIOTypes(sf);
	
		
		String lastNodeOut = null;
		
		if (compoundNode.getInputs().size() > 0) {
			lastNodeOut = "in";
		}
		
		//call nodes in order
		
		//deal with first to second to last
		
		out.getWriter(M_DUMMY_THREAD_C).println("int err;");
		
		for (int i = 0; i < (args.size() - 1); i++) {
			TaskDeclaration node = program.getTask(args.get(i));
			
			if (node == null) {
				//
				System.err.printf(ERR_NODE_NOT_FOUND, node.getName());
				System.exit(1);
			}
			
			//out variable

			out.getWriter(M_DUMMY_THREAD_C).println(getOutputName(node)+
					" " + node.getName() + "_var_out;");
			out.getWriter(M_DUMMY_THREAD_C).println(node.getName() + 
					"_var_out._mSession = in->_mSession;");
			
			generateSimpleCall(node, lastNodeOut, "&" + node.getName() + 
					"_var_out", false);
			
			lastNodeOut = "&" + node.getName() + "_var_out";
		}
		
		//last element (uses global output)
		TaskDeclaration node = program.getTask(args.lastElement());
		
		if (node == null) {
			//
			System.err.printf(ERR_NODE_NOT_FOUND, node.getName());
			System.exit(1);
		}
		if (compoundNode.getOutputs().size() > 0)		
			generateSimpleCall(node, lastNodeOut, "out", false);
		else 
			generateSimpleCall(node, lastNodeOut, null, false);
		//return if successful
		out.getWriter(M_DUMMY_THREAD_C).println("return 0;\n}\n");
	}
	
	/**
	 * Generates a compound node that has a type dispatch in it
	 * @param tf objects representing
	 */
	protected void generateTypedDispatch(TypedFlowStatement tf) {
		TaskDeclaration compoundNode = program.getTask(tf.getAssignee());
		Vector<FlowStatement> cases = tf.getFlowStatements();
		//generate all nodes required
		for (Iterator<FlowStatement> fi = cases.iterator(); fi.hasNext();) {
			SimpleFlowStatement sfs = (SimpleFlowStatement)fi.next();
			for (Iterator<String> ti = sfs.getArguments().iterator(); ti.hasNext();) //{
				generateTask(program.getTask(ti.next()));
								//(sfs).getArguments().firstElement()));
			//}
		}
		//generate the prototype
		generateInternalTaskPrototype(compoundNode);
		
		generateIOTypes(tf);

		out.getWriter(M_DUMMY_THREAD_C).println("int err;");
		//generate branches
		for (Iterator<FlowStatement> fi = cases.iterator(); fi.hasNext();) {
			SimpleFlowStatement sf = (SimpleFlowStatement)fi.next();
			generateOneBranch(sf);
		}
		
		out.getWriter(M_DUMMY_THREAD_C).println("return 0;}\n");
		
	}
	
	protected void generateIOTypes(TypedFlowStatement tf) {
		String realType = 
			getInputName(program.getTask(((SimpleFlowStatement)(tf.getFlowStatements().
					firstElement())).getArguments().firstElement()));
		String alias = getInputName(program.getTask(tf.getAssignee()));
		if (typeAliases.containsKey(realType))
			realType = typeAliases.get(realType);
		else
			typeAliases.put(alias, realType);
		
		out.getWriter(M_STRUCT_H).println("typedef " + realType +
				" " + alias + ";");
		
		realType = 
			getOutputName(program.getTask(((SimpleFlowStatement)(tf.getFlowStatements().
					firstElement())).getArguments().lastElement()));
		alias = getOutputName(program.getTask(tf.getAssignee()));
		
		if (typeAliases.containsKey(realType))
			realType = typeAliases.get(realType);
		else
			typeAliases.put(alias, realType);
		
		out.getWriter(M_STRUCT_H).println("typedef " + realType +
				" " + alias + ";");
		
	}
	
	protected void generateIOTypes(SimpleFlowStatement sf) {
		String realType = getInputName(program.getTask(sf.getArguments().firstElement()));
		String alias = getInputName(program.getTask(sf.getAssignee()));
		if (alias != null) {
			if (typeAliases.containsKey(realType))
				realType = typeAliases.get(realType);
			else 
				typeAliases.put(alias, realType);
			
			out.getWriter(M_STRUCT_H).println("typedef " + realType + " " +
				alias + ";");
		}	
		realType = getOutputName(program.getTask(sf.getArguments().lastElement()));
		alias = getOutputName(program.getTask(sf.getAssignee()));
		if (alias != null) {
			if (typeAliases.containsKey(realType))
				realType = typeAliases.get(realType);
			else 
				typeAliases.put(alias, realType);
		
			out.getWriter(M_STRUCT_H).println("typedef " + realType + " " +
					alias + ";");
		}
	}
	
	/**
	 * Generates one branch described by one SimpleFlowStatement
	 * @param sf object descibibg the branch
	 */
	protected void generateOneBranch(SimpleFlowStatement sf) {
		Vector<String> types = sf.getTypes();
		Vector<String> args = sf.getArguments();
		Vector<Argument> inputs = program.getTask(sf.getArguments().firstElement()).getInputs();
		//if
		out.getWriter(M_DUMMY_THREAD_C).print("if ((1)");
		//
		for (int i = 0; i < types.size(); i++) {
			String typeString = types.get(i);
		
			if (!typeString.equals("*")) {
				TypeDeclaration type = program.getType(typeString);
		
				generateTypeCheckProto(type, inputs.get(i));
				
				out.getWriter(M_DUMMY_THREAD_C).print("&&(" + type.getFunction() + 
						"(in->" + inputs.get(i).getName() + "))");
			}
		}
		//call the fucntion
		out.getWriter(M_DUMMY_THREAD_C).println(") {");
		
		String lastNodeOut = null;
		
		if (inputs.size() > 0) {
			lastNodeOut = "in";
		}
		
		//call nodes in order
		
		//deal with first to second to last
		
		out.getWriter(M_DUMMY_THREAD_C).println("int err;");
		
		for (int i = 0; i < (args.size() - 1); i++) {
			TaskDeclaration node = program.getTask(args.get(i));
			
			if (node == null) {
				//
				System.err.printf(ERR_NODE_NOT_FOUND, node.getName());
				System.exit(1);
			}
			
			//out variable

			out.getWriter(M_DUMMY_THREAD_C).println(getOutputName(node)+
					" " + node.getName() + "_var_out;");
			out.getWriter(M_DUMMY_THREAD_C).println(node.getName() + 
					"_var_out._mSession = in->_mSession;");
			
			generateSimpleCall(node, lastNodeOut, "&" + node.getName() + 
					"_var_out", false);
			
			lastNodeOut = "&" + node.getName() + "_var_out";
		}
		
		//last element (uses global output)
		TaskDeclaration node = program.getTask(args.lastElement());
		
		if (node == null) {
			//
			System.err.printf(ERR_NODE_NOT_FOUND, node.getName());
			System.exit(1);
		}
		if (program.getTask(sf.getAssignee()).getOutputs().size() > 0)		
			generateSimpleCall(node, lastNodeOut, "out", false);
		else 
			generateSimpleCall(node, lastNodeOut, null, false);
		
		/*TaskDeclaration task = program.getTask(sf.getArguments().firstElement());
		generateSimpleCall(task,
				(task.getInputs().size() > 0)?"in":null, 
				(task.getOutputs().size() > 0)?"out":null,
				 false);*/
		//exit
		out.getWriter(M_DUMMY_THREAD_C).println("return 0;\n}");
		//return task;
	}
	
	/**
	 * Generates code corresponding to a flow statement, typed or untyped
	 * @param f object describing the flow.
	 */
	protected void generateFromFlowStatement(FlowStatement f) {
		TaskDeclaration compoundNode = program.getTask(f.getAssignee());
		
		if (compoundNode == null) {
			//node has to be declared
			System.err.printf(ERR_NODE_NOT_FOUND, f.getAssignee());
			System.exit(1);
		}
	
		//depending on the type of flow statement
		if (f instanceof SimpleFlowStatement) {
			SimpleFlowStatement sf = (SimpleFlowStatement) f;
			//After a call to unifyExpressions only untyped SimpleFlowStatements
			//should be left
			if (sf.getTypes() != null) {
				System.exit(1);
			}
			//regular untyped flow
			generateFromUntypedFlow(sf);
					
		}
		else if (f instanceof TypedFlowStatement) {
			TypedFlowStatement tf = (TypedFlowStatement) f;
			//generate branching point
			generateTypedDispatch(tf);
		}
	}
	
	/**
	 * Generates prototype for typecheck
	 * @param type
	 * @param arg
	 */
	protected void generateTypeCheckProto(TypeDeclaration type, Argument arg) {
		//		prototype
		out.getWriter(M_IMPL_H).println("bool " + type.getFunction() + "(" + 
				arg.getType() + " in);\n");
		//stub
		out.getWriter(M_IMPL_C).println("bool " + type.getFunction() + "(" + 
				arg.getType() + " in) {\n}\n");
	}
	
	/**
	 * Generates call that spawn a new thread and deletes all the data upon 
	 * completion
	 * @param function function to call
	 * @param input input to the function
	 */
	protected void generateCallIntoThread(String function, String input) {
		out.getWriter(M_DUMMY_THREAD_C).println(
				"pthread_t *thread = (pthread_t *) calloc(1, sizeof(pthread_t));");
		//closure
		out.getWriter(M_DUMMY_THREAD_C).println(
				"function_closure *closure = (function_closure *)calloc(1, sizeof(function_closure));");
		out.getWriter(M_DUMMY_THREAD_C).println("closure->data = (void *)" + input + ";");
		  
		out.getWriter(M_DUMMY_THREAD_C).println("closure->run = (run_func)" + function + ";");
		  //thread+closure pair
		out.getWriter(M_DUMMY_THREAD_C).println("pair * _pair = (pair *)calloc(1, sizeof(pair));");
		  
		out.getWriter(M_DUMMY_THREAD_C).println("_pair->first = thread;");
		out.getWriter(M_DUMMY_THREAD_C).println("_pair->second = closure;"); 
		  
		out.getWriter(M_DUMMY_THREAD_C).println("pthread_create(thread, NULL, runAndDelete, (void *)_pair);");
		out.getWriter(M_DUMMY_THREAD_C).println("pthread_detach(*thread);");

	}
	
	/**
	 * Generates the entire program
	 * @param sf
	 */
	protected void generateSourceHandler(Source sf) {
		TaskDeclaration source = program.getTask(sf.getSourceFunction());
		TaskDeclaration destination = program.getTask(sf.getTarget());
		
		generateTask(source);
		
		generateTask(destination);
				
		out.getWriter(M_DUMMY_THREAD_C).println("void *" + 
				sf.getSourceFunction() + "_source_handler(void *a) {");
		out.getWriter(M_PROTO_H).println("void *" + 
				sf.getSourceFunction() + "_source_handler(void *a);\n");
		
		
		//declare out var
		out.getWriter(M_DUMMY_THREAD_C).println(getOutputName(source) + " *out;");
		//in a loop
		out.getWriter(M_DUMMY_THREAD_C).println("  while(running) {");
	
		out.getWriter(M_DUMMY_THREAD_C).println("out = (" + getOutputName(source) + 
				"*)malloc(sizeof(" + getOutputName(source) + "));");
		out.getWriter(M_DUMMY_THREAD_C).println("out->_mSession = get_new_session();");
		out.getWriter(M_DUMMY_THREAD_C).println("int err;");
		generateSimpleCall(source, null, 
				(source.getOutputs().size() > 0)?"out":null, 
				true);
		
		generateCallIntoThread(destination.getName(), "out");
		
		out.getWriter(M_DUMMY_THREAD_C).println("}\n}");
		
  	}
	
	/**
	 * Generate a threaded program
	 **/
	public void generate() throws IOException {

		prologue();
    	
		program.unifyExpressions();
    	
    	//process flow statements
    	Collection<FlowStatement> flows = program.getExpressions();
    	
    	for (Iterator<FlowStatement> flowi = flows.iterator(); flowi.hasNext();){
    		generateTask(program.getTask(flowi.next().getAssignee()));	
    	}
    	
    	//process sources
    	Vector<Source> sources = program.getSources();
    	for(Iterator<Source> it  = sources.iterator(); it.hasNext();) {
    		generateSourceHandler(it.next());
    	}
    	
    	//generate main
    	out.getWriter(M_DUMMY_THREAD_C).println("int main(int argc, char **argv) {");
    	out.getWriter(M_DUMMY_THREAD_C).println("init(argc, argv);");
    	    	
    	out.getWriter(M_DUMMY_THREAD_C).println("pthread_t *thread;");
    	
    	for (Iterator<Source> i = sources.iterator(); i.hasNext();) {
    		//Create a source-handling function
    		Source s = i.next();
    		out.getWriter(M_DUMMY_THREAD_C).println(
    				"thread = (pthread_t*)malloc(sizeof(pthread_t));");
    		out.getWriter(M_DUMMY_THREAD_C).println("pthread_create(thread, NULL, " +
    				s.getSourceFunction() + "_source_handler, NULL);");
    		out.getWriter(M_DUMMY_THREAD_C).println("pthread_detach(*thread);");
    		
    	}
    	    	    	
    	out.getWriter(M_DUMMY_THREAD_C).println("return 0;\n}");
    	epilogue();

        out.flushAndClose();
	}
}