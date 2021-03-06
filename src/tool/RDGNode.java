package tool;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import fdtmc.FDTMC;


public class RDGNode {

	//This reference is used to store all the RDGnodes created during the evaluation
	private static Map<String, RDGNode> rdgNodes = new HashMap<String, RDGNode>();
	private static List<RDGNode> nodesInCreationOrder = new LinkedList<RDGNode>();

    private static int lastNodeIndex = 0;

	private RDGNodeData data = new RDGNodeData();


	/**
	 * The id, presence condition and model (FDTMC) of an RDG node must
	 * be immutable, so there must be no setters for them. Hence, they
	 * must be set at construction-time.
	 *
	 * @param id Node's identifier. It is preferably a valid Java identifier.
	 * @param presenceCondition Boolean expression over features (using Java operators).
	 * @param fdtmc Stochastic model of the piece of behavioral model represented by
	 *             this node.
	 */
	public RDGNode(String id, String presenceCondition, FDTMC fdtmc) {
	    this.data.setId(id);
	    this.data.setPresenceCondition(presenceCondition);
	    this.data.setFdtmc(fdtmc);
		this.data.setDependencies(new HashSet<RDGNode>());
		this.data.setHeight(0);

		rdgNodes.put(id, this);
		nodesInCreationOrder.add(this);
	}

	public void addDependency(RDGNode child) {
        this.data.dependencies.add(child);
        data.height = Math.max(data.height, child.data.height + 1);
	}

    public static RDGNode getById(String id) {
        return rdgNodes.get(id);
    }

    public static String getNextId() {
        return "n" + lastNodeIndex++;
    }

    /**
     * We consider two RDG nodes to be equal whenever their behavior is
     * modeled by equal FDTMCs, their presence condition is the same and
     * their dependencies are also correspondingly equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (notNullAndSameInstance(obj)) {
            RDGNode other = (RDGNode) obj;
            return hasSamePresenceCondition(other)
                    && hasSameFDTMC(other)
                    && hasSameDependencies(other);
        }
        return false;
    }

	private boolean notNullAndSameInstance(Object obj) {
		return obj != null && obj instanceof RDGNode;
	}

	private boolean hasSameDependencies(RDGNode other) {
		return this.data.getDependencies().equals(other.data.getDependencies());
	}

	private boolean hasSameFDTMC(RDGNode other) {
		return this.data.getFdtmc().equals(other.data.getFdtmc());
	}

	private boolean hasSamePresenceCondition(RDGNode other) {
		return this.data.getPresenceCondition().equals(other.data.getPresenceCondition());
	}

    @Override
    public int hashCode() {
        return data.id.hashCode() + data.presenceCondition.hashCode() + data.fdtmc.hashCode() + data.dependencies.hashCode();
    }

    @Override
    public String toString() {
        return data.getId() + " (" + data.getPresenceCondition() + ")";
    }

    /**
     * Retrieves the transitive closure of the RDGNode dependency relation.
     * The node itself is part of the returned list.
     *
     * It implements the Cormen et al.'s topological sort algorithm.
     *
     * @return The descendant RDG nodes ordered bottom-up (depended-upon to dependent).
     * @throws CyclicRdgException if there is a path with a cycle starting from this node.
     */
    public List<RDGNode> getDependenciesTransitiveClosure() throws CyclicRdgException {
        List<RDGNode> transitiveDependencies = new LinkedList<RDGNode>();
        Map<RDGNode, Boolean> marks = new HashMap<RDGNode, Boolean>();
        topoSortVisit(this, marks, transitiveDependencies);
        return transitiveDependencies;
    }

    /**
     * Topological sort {@code visit} function (Cormen et al.'s algorithm).
     * @param node
     * @param marks
     * @param sorted
     * @throws CyclicRdgException
     */
    private void topoSortVisit(RDGNode node, Map<RDGNode, Boolean> marks, List<RDGNode> sorted) throws CyclicRdgException {
        if (containsKeyAndNotHaveNode(node, marks)) {
            // Visiting temporarily marked node -- this means a cyclic dependency!
            throw new CyclicRdgException();
        } else if (doesNotContainKey(node, marks)) {
            // Mark node temporarily (cycle detection)
            addNodeToMap(node, marks, false);
            getDependenciesForEachChild(node, marks, sorted);
            // Mark node permanently (finished sorting branch)
            addNodeToMap(node, marks, true);
            sorted.add(node);
        }
    }

	private static boolean doesNotContainKey(RDGNode node, Map<RDGNode, Boolean> marks) {
		return !marks.containsKey(node);
	}

	private void getDependenciesForEachChild(RDGNode node, Map<RDGNode, Boolean> marks, List<RDGNode> sorted)
			throws CyclicRdgException {
		for (RDGNode child: node.data.getDependencies()) {
		    topoSortVisit(child, marks, sorted);
		}
	}

    /**
     * Computes the number of paths from source nodes to every known node.
     * @return A map associating an RDGNode to the corresponding number
     *      of paths from a source node which lead to it.
     * @throws CyclicRdgException
     */
    public Map<RDGNode, Integer> getNumberOfPaths() throws CyclicRdgException {
        Map<RDGNode, Integer> numberOfPaths = new HashMap<RDGNode, Integer>();

        Map<RDGNode, Boolean> marks = new HashMap<RDGNode, Boolean>();
        Map<RDGNode, Map<RDGNode, Integer>> cache = new HashMap<RDGNode, Map<RDGNode,Integer>>();
        Map<RDGNode, Integer> tmpNumberOfPaths = numPathsVisit(this, marks, cache);
        numberOfPaths = sumPaths(numberOfPaths, tmpNumberOfPaths);

        return numberOfPaths;
    }

    // TODO Parameterize topological sort of RDG.
    private static Map<RDGNode, Integer> numPathsVisit(RDGNode node, Map<RDGNode, Boolean> marks, Map<RDGNode, Map<RDGNode, Integer>> cache) throws CyclicRdgException {
        if (containsKeyAndNotHaveNode(node, marks)) {
            // Visiting temporarily marked node -- this means a cyclic dependency!
            throw new CyclicRdgException();
        } else if (doesNotContainKey(node, marks)) {
            // Mark node temporarily (cycle detection)
            addNodeToMap(node, marks, false);

            Map<RDGNode, Integer> numberOfPaths = new HashMap<RDGNode, Integer>();
            // A node always has a path to itself.
            numberOfPaths.put(node, 1);
            // The number of paths from a node X to a node Y is equal to the
            // sum of the numbers of paths from each of its descendants to Y.
            numberOfPaths = getDependenciesForEachChild(node, marks, cache, numberOfPaths);
            // Mark node permanently (finished sorting branch)
            addNodeToMap(node, marks, true);
            cache.put(node, numberOfPaths);
            return numberOfPaths;
        }
        // Otherwise, the node has already been visited.
        return cache.get(node);
    }

	private static void addNodeToMap(RDGNode node, Map<RDGNode, Boolean> marks, boolean bool) {
		marks.put(node, bool);
	}

	private static boolean containsKeyAndNotHaveNode(RDGNode node, Map<RDGNode, Boolean> marks) {
		return marks.containsKey(node) && marks.get(node) == false;
	}

	private static Map<RDGNode, Integer> getDependenciesForEachChild(RDGNode node, Map<RDGNode, Boolean> marks,
			Map<RDGNode, Map<RDGNode, Integer>> cache, Map<RDGNode, Integer> numberOfPaths) throws CyclicRdgException {
		for (RDGNode child: node.data.getDependencies()) {
		    Map<RDGNode, Integer> tmpNumberOfPaths = numPathsVisit(child, marks, cache);
		    numberOfPaths = sumPaths(numberOfPaths, tmpNumberOfPaths);
		}
		return numberOfPaths;
	}

    /**
     * Sums two paths-counting maps
     * @param pathsCountA
     * @param pathsCountB
     * @return
     */
    private static Map<RDGNode, Integer> sumPaths(Map<RDGNode, Integer> pathsCountA, Map<RDGNode, Integer> pathsCountB) {
        Map<RDGNode, Integer> numberOfPaths = new HashMap<RDGNode, Integer>(pathsCountA);
        pathsCountBEntrySetForEachEntry(pathsCountB, numberOfPaths);
        return numberOfPaths;
    }

	private static void pathsCountBEntrySetForEachEntry(Map<RDGNode, Integer> pathsCountB,
			Map<RDGNode, Integer> numberOfPaths) {
		for (Map.Entry<RDGNode, Integer> entry: pathsCountB.entrySet()) {
            RDGNode node = entry.getKey();
            Integer count = entry.getValue();
            if (numberOfPaths.containsKey(node)) {
                count += numberOfPaths.get(node);
            }
            numberOfPaths.put(node, count);
        }
	}

    /**
     * Returns the first RDG node (in crescent order of creation time) which is similar
     * to the one provided.
     *
     * A similar RDG node is one for which equals() returns true.
     * @param rdgNode
     * @return a similar RDG node or null in case there is none.
     */
    public static RDGNode getSimilarNode(RDGNode target) {
        for (RDGNode candidate: nodesInCreationOrder) {
            if (candidate != target && candidate.equals(target)) {
                return candidate;
            }
        }
        return null;
    }

}
