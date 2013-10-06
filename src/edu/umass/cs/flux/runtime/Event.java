package edu.umass.cs.flux.runtime;

public class Event {
    TaskBase data;
    int[] stack;
    int stack_pointer;
    int type;
    
    public Event(TaskBase data) {
	this.data = data;
	stack = new int[20];
	stack_pointer = -1;
    }
    
    public int getType() {
	return type;
    }

    public void setType(int type) {
	this.type = type;
    }

    public void push(int route) {
	stack_pointer++;
	if (stack_pointer == stack.length) {
	    int[] tmp = new int[stack.length*2];
	    for (int i=0;i<stack.length;i++)
		tmp[i] = stack[i];
	    stack = tmp;
	}
	stack[stack_pointer] = route;
    }
    
    public int pop() {
	if (stack_pointer < 0)
	    throw new IllegalStateException("Empty stack!");
	return stack[stack_pointer--];
    }

    public TaskBase getData() {
	return data;
    }

    public void setData(TaskBase data) {
	this.data = data;
    }
}
