package edu.umass.cs.flux;

import java.util.*;
import java.io.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import jdsl.graph.ref.IncidenceListGraph;
import jdsl.graph.api.*;

/**
 * This Class takes in a ProgramGraph representation of a Flux graph
 * and outputs a CSIM file that can be used to simulate the cunning time.
 * 
 **/
public class SimulatorGenerator 
{
	ProgramGraph programGraph;

	Collection<TaskDeclaration> functions;

	Collection<Source> sources;

	IncidenceListGraph graph;

	Program program;
	
	//Vector<Vertex> functionVector;
	SimulatorGenerator(Program p, ProgramGraph pg, String root) 
	{
		program = p;
		programGraph = pg;
		functions = programGraph.getProgram().getFunctions();
		sources = programGraph.getSources();
		graph = programGraph.graph;
		makeCSimProgram(root);
	}

	private void makeCSimProgram(String root) 
	{
		try 
		{
			VirtualDirectory virtDir = new VirtualDirectory(root);

			PrintWriter out = new PrintWriter(new BufferedWriter(virtDir
					.getWriter("simulator.cpp")));

			// includes here
			out.println("#include \"cpp.h\"");
			out.println("#include <stdio.h>");
			out.println("#include \"flux_csim_includes.h\"");

			out.println("\n");

			// output file pointer
			out.println("FILE *fp;\n");
			out.println("#define NUM_JOBS\t100000");
			//out.println("const long numJobs = 100000;");

			// now we do completions counters
			out.println("\n//completions counters\n");
			for (Source s : sources) {
				Vertex vertex = (Vertex) programGraph.vertex_map.get(s
						.getSourceFunction());
				//<node_name>_completion_counter = 0;
				String currCompletioinCounter = s.getSourceFunction() + "_completion_counter";
				out.println("unsigned int " + currCompletioinCounter + " = 0;");
			}
			
			out.println("event *done;");
			out.println("facility *processor;\n");
			out.println("// Now we declare the global locks");
			for (TaskDeclaration td : functions) 
			{
				AtomicDeclaration a = program.getAtomicDeclaration(td.getName());
				if (a != null)
				{
					for(Lock l: a.getLocks())
					{
						if(l.isProgram())
						{
							out.println("facility *" + l.getName() + "_program_lock;");
						}
					}
				}
			}
			out.println("\n");
			out.println("//TASK DECLS");
			for (TaskDeclaration td : functions) 
			{
				out.println("void " + td.getName() + "();");
				if (td.getErrorHandler() != null)
				{
					out.println("void " + td.getErrorHandler() + "();");
				}
			}
			
			out.println("void init();");
			out.println("extern \"C\" void sim();");

			out.println("\n");

			// output the main function 
			printMain(out);
			
			// output the "sim" function
			printSim(out);
			
			// output the init() function here
			printInit(out);

			// insert intialization here...
			out.println("//SOURCES");
			for (Source s : sources) 
			{
				Vertex vertex = (Vertex) programGraph.vertex_map.get(s.getSourceFunction());
				printFunction(out, vertex);
			}

			out.flush();
			out.close();
		} 
		catch (Exception e) 
		{
			System.out.println(e);
		}
	}

	private void printSim(PrintWriter out)
	{
		out.println("extern \"C\" void sim(){");
		
		out.println("create(\"sim\");");
		out.println("init();");
		
		out.println("for(int i=0; i < NUM_JOBS; i++){");
		
		for (Source s : sources) {
			Vertex vertex = (Vertex) programGraph.vertex_map.get(s
					.getSourceFunction());
			// out.println("");
			String str = "IA_TIME_" + s.getSourceFunction().toUpperCase(); 
			out.println("hold(exponential(" + str +"));");
			out.println(s.getSourceFunction() + "();");
		}
		
		out.println("}");
		out.println("}");
	}
	
	
	/*
	 * The init() function sets up all of the facilities (shared resources). 
	 * Currently, "processor" is a simple "facility" object, but if we want to
	 * simmulate a multiple cpu machine we have to use "facility_ms".
	 * */
	private void printInit(PrintWriter out) 
	{
		out.println("void init(){");
		out.println("processor = new facility(\"processor\");");
		
		for (TaskDeclaration td : functions) {
			AtomicDeclaration a = program.getAtomicDeclaration(td.getName());
			if (a != null)
			{
				for(Lock l: a.getLocks())
				{
					if(l.isProgram())
					{
						String lockName = l.getName() + "_program_lock";
						out.println(lockName + " = new facility(\"" + lockName + "\");");
					}
				}
			}
		}
		
		out.println("}");
	}

	private void printMain(PrintWriter out) 
	{
		out.println("int main(){");
		out.println("fp = fopen(\"results.out\", \"w\");");
		out.println("for (int i = 0; i < 3; i++){");
		out.println("fprintf(fp, \"Start Trial: %i\", i);");
		out.println("sim();");
		out.println("fprintf(fp, \"End Trial: %i\", i);");
		out.println("}");
		out.println("return 0;");
		out.println("}\n");
	}

	private void printFunction(PrintWriter out, Vertex vertex) {
		GraphNode gNode = (GraphNode) vertex.element();
		Vector<Vertex> vertexVector = new Vector<Vertex>();

		EdgeIterator edgeIterator = this.graph.incidentEdges(vertex,
				EdgeDirection.OUT);
		while (edgeIterator.hasNext()) {
			Edge edge = edgeIterator.nextEdge();
			Vertex destination = this.graph.destination(edge);
			GraphNode graphNode = (GraphNode) destination.element();
			int nodeType = graphNode.getNodeType();
			if (nodeType != GraphNode.ENTRY && nodeType != GraphNode.EXIT
					&& nodeType != GraphNode.ERROR) {
				vertexVector.add(destination);
			}
		}
		printFunction(out, gNode.toString(), vertexVector, gNode.toString());
	}

	private void printFunction(PrintWriter out, String fnName,
			Vector<Vertex> vertexVector, String currSrcFn) {
		out.println("void " + fnName + "(){");
		
		String programLock = null;
		
		// check if there are any locks
		AtomicDeclaration a = program.getAtomicDeclaration(fnName);
		if (a != null)
		{
			Vector<Lock> locks = a.getLocks();
			for(Lock l: locks)
			{
				if (l.isProgram())
				{
					programLock = l.getName() + "_program_lock";
					out.println(programLock + "->reserve();");
				}
			}
		}
		String str_cpu = "CMP_TIME_CPU_" + fnName.toUpperCase();
		String str_clock = "CMP_TIME_CLOCK_" + fnName.toUpperCase();
		
		out.println("processor->reserve();");
		out.println("hold(exponential(" + str_cpu + "));");
		out.println("processor->release();");
		
		out.println("hold(exponential(" + str_clock +  " - " + str_cpu + "));");
		
		if (programLock != null)
		{
			out.println(programLock + "->release();");
		}
		
		if (vertexVector.size() == 0) 
		{
			/*
			 * Completion Counter
			 * int <source_node_name>_completion_counter = 0;
			 * */
			//TaskDeclaration td =
			//graph.
			Vertex v = programGraph.getVertex(fnName);
			//TaskDeclaration task = (TaskDeclaration)v.element();
			GraphNode g = (GraphNode)v.element();
			if (g.getNodeType() == GraphNode.ERROR_HANDLER) 
			{
				out.println("// this is an error");
			}
			else
			{
			out.println(currSrcFn + "_completion_counter++;");
			}
			//out.println("//this is a leaf node!!!");
			out.println("return;");
			out.println("}\n");
		} 
		// if this node can only go to one other node (besides erroring out)
		else if (vertexVector.size() == 1) 
		{
			Vertex vertex = vertexVector.elementAt(0);
			GraphNode gNode = (GraphNode) vertex.element();
			out.println("double ret = uniform(0.0, 1.0);");
			String prob = "PROB_" + fnName.toUpperCase() + "_TO_" + gNode.toString().toUpperCase();
			out.println("if(ret <= " + prob + "){");
			out.println(gNode.toString() + "();");
			out.println("return;");
			out.println("}");
			out.println("return;");
			out.println("}\n");

			
			// now we generate the children
			Vector<Vertex> vVector = new Vector<Vertex>();
			EdgeIterator edgeIterator = this.graph.incidentEdges(vertex, EdgeDirection.OUT);
			while (edgeIterator.hasNext())
			{
				Edge edge = edgeIterator.nextEdge();
				Vertex destination = this.graph.destination(edge);
				GraphNode graphNode = (GraphNode) destination.element();
				int nodeType = graphNode.getNodeType();
				if (nodeType != GraphNode.ENTRY && nodeType != GraphNode.EXIT && nodeType != GraphNode.ERROR) 
				{
					vVector.add(destination);
				}
			}
			GraphNode node = (GraphNode) vertex.element();
			printFunction(out, node.toString(), vVector, currSrcFn);

		} 
		else 
		{
			out.println("double ret = uniform(0.0, 1.0);");
			out.println("double curr_prob = 0.0;");
			out.println("double lower_prob = 0.0;");
			out.println("double higher_prob = 0.0;");

			Vector<String> vectorString = new Vector<String>();

			for (int i = 0; i < vertexVector.size(); i++) {
				Vertex vertex = vertexVector.elementAt(i);
				GraphNode graphNode = (GraphNode) vertex.element();

				out.println("lower_prob = higher_prob;");
				String prob = "PROB_" + fnName.toUpperCase() + "_TO_"
						+ graphNode.toString().toUpperCase();
				out.println("higher_prob += " + prob + ";");

				out.println("if (ret > lower_prob  && ret <= higher_prob){");
				out.println(graphNode.toString() + "();");
				out.println("return;");
				out.println("}");
			}
			out.println("}\n\n");

			// now we generate the children
			Vector<Vertex> vVector = new Vector<Vertex>();
			for (int i = 0; i < vertexVector.size(); i++) {
				Vertex vertex = vertexVector.elementAt(i);
				EdgeIterator edgeIterator = this.graph.incidentEdges(vertex,
						EdgeDirection.OUT);
				while (edgeIterator.hasNext()) {
					Edge edge = edgeIterator.nextEdge();
					Vertex destination = this.graph.destination(edge);
					GraphNode graphNode = (GraphNode) destination.element();
					int nodeType = graphNode.getNodeType();
					if (nodeType != GraphNode.ENTRY
							&& nodeType != GraphNode.EXIT
							&& nodeType != GraphNode.ERROR) {
						vVector.add(destination);
					}
				}
				GraphNode gNode = (GraphNode) vertex.element();
				printFunction(out, gNode.toString(), vVector, currSrcFn);
			}
		}
	}

	public static void main(String[] args) {

	}
}



/*
 * Here is how different variables are named:
 * 
 * Probabilities:
 * PROB_<src>_TO_<dest>
 * 
 * Inter-Arrival Time
 * IA_TIME_<node-name>
 * 
 * CPU Completion Time
 * CMP_TIME_CPU_<node name>
 * 
 * Wall-Clock Completion Time
 * CMP_TIME_CLOCK_<node name>
 * 
 * Program Locks
 * facility *<node_name>_program_lock;
 * 
 * Completion Counter
 * int <source_node_name>_completion_counter = 0;
 * 
 * */
