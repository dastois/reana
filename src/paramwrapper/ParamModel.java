package paramwrapper;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fdtmc.FDTMC;
import fdtmc.State;
import fdtmc.Transition;



class ParamModel {
	private String stateVariable = "s";
	// TODO Deixar nome do módulo PARAM configurável.
	private final String moduleName = "dummyModule";
	// TODO Inferir estado inicial a partir da topologia da FDTMC.
	private int initialState = 0;

	private final Set<String> parameters;
	private final Map<String, Set<Integer>> labels;
	private final Map<Integer, Command> commands;

	private final int stateRangeStart;
	private final int stateRangeEnd;

	public ParamModel(FDTMC fdtmc) {
		if (fdtmc.getVariableName() != null) {
			stateVariable = fdtmc.getVariableName();
		}
		initialState = fdtmc.getInitialState().getIndex();
		commands = getCommands(fdtmc);
		labels = getLabels(fdtmc);
		stateRangeStart = Collections.min(commands.keySet());
		// PARAM não deixa declarar um intervalo com apenas um número.
		stateRangeEnd = Math.max(stateRangeStart + 1,
								 Collections.max(commands.keySet()));
		parameters = getParameters(commands.values());
	}

	private Map<String, Set<Integer>> getLabels(FDTMC fdtmc) {
		Map<String, Set<Integer>> labeledStates = new TreeMap<String, Set<Integer>>();
		Collection<State> states = fdtmc.getStates();
		for (State actualState : states) {
			String label = actualState.getLabel();
			if (label != null) {
				if (!labeledStates.containsKey(label)) {
					labeledStates.put(label, new TreeSet<Integer>());
				}
				labeledStates.get(label).add(actualState.getIndex());
			}
		}
		return labeledStates;
	}

	private Map<Integer, Command> getCommands(FDTMC fdtmc) {
		Map<Integer, Command> tmpCommands = new TreeMap<Integer, Command>();
		for (Entry<State, List<Transition>> entry : fdtmc.getTransitions().entrySet()) {
		    int initState = entry.getKey().getIndex();
			Command command = new Command(initState);
			if (entry.getValue() != null) {
			    for (Transition transition : entry.getValue()) {
			        command.addUpdate(transition.getProbability(),
			                          transition.getTarget().getIndex());
			    }
			} else {
			    // Workaround: manually adding self-loops in case no
			    // transition was specified for a given state.
			    command.addUpdate("1", initState);
			}
			tmpCommands.put(initState, command);
		}
		return tmpCommands;
	}

	private Set<String> getParameters(Collection<Command> commands) {
		Set<String> tmpParameters = new HashSet<String>();

		Pattern validIdentifier = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
		for (Command command : commands) {
			for (String probability : command.getUpdatesProbabilities()) {
				Matcher matcherToFind = validIdentifier.matcher(probability);
				while (matcherToFind.find()) {
					tmpParameters.add(matcherToFind.group());
				}
			}
		}
		return tmpParameters;
	}

	@Override
	public String toString() {
		String params = "";
		for (String parameter : parameters) {
			params += "param double "+parameter+";\n";
		}
		String module = moduleString(params);
		for (Command command : commands.values()) {
			module += "	"+command.makeString(stateVariable) + "\n";
		}
		module += "endmodule\n\n";
		for (Map.Entry<String, Set<Integer>> entry : labels.entrySet()) {
			String label = entry.getKey();
			module += "label \""+label+"\" = ";

			Set<Integer> states = entry.getValue();
			int count = 1;
			module = changeModuleState(module, states, count);
			module += ";\n";
		}
		return module;
	}

	private String changeModuleState(String module, Set<Integer> states, int count) {
		for (Integer state : states) {
			module += stateVariable+"="+state;
			if (count < states.size()) {
				module += " | ";
			}
			count++;
		}
		return module;
	}
	
	private String moduleString(String params) {
		return 	"dtmc\n" +
				"\n" +
				params +
				"\n" +
				"module " + moduleName + "\n" +
				"	"+stateVariable+ " : ["+stateRangeStart+".."+stateRangeEnd+"] init "+initialState+";" +
				"\n";
	}
}

class Command {
	private int initialState;
	private List<String> updatesProbabilities;
	private List<Integer> updatesActions;

	public Command(int initialState) {
		this.initialState = initialState;
        this.updatesProbabilities = new LinkedList<String>();
        this.updatesActions = new LinkedList<Integer>();
	}

	public void addUpdate(String probability, int update) {
		updatesProbabilities.add(probability);
		updatesActions.add(update);
	}

	public Collection<String> getUpdatesProbabilities() {
		return updatesProbabilities;
	}

	public String makeString(String stateVariable) {
		String command = "[] "+stateVariable+"="+initialState+" -> ";
		boolean needsPlus = false;
		for (int i = 0; i < updatesProbabilities.size(); i++) {
		    if (needsPlus) {
		        command += " + ";
		    } else {
		        needsPlus = true;
		    }
			command += "("+updatesProbabilities.get(i)+") : ("+stateVariable+"'="+updatesActions.get(i)+")";
		}
		return command+";";
	}
}