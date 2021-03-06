package com.alibaba.jstorm.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class CommunicationPipeLine {
    private Logger LOG = LoggerFactory.getLogger(CommunicationPipeLine.class);

    private TreeSet<Integer> rootTaskIds = new TreeSet<Integer>();

    private boolean nodePipe = true;

    private PipeLineNode rootNode;

    private class PipeLineNode {
        int previousTask; // the previous task
        int sourceTask;   // which node of this set of nodes that act as the gateway
        int targetTask;   // the target task
        Set<Integer> inMemoryTasks = new HashSet<Integer>(); // the tasks other than the source, which are in the same worker

        public PipeLineNode(int previousTask) {
            this.previousTask = previousTask;
        }

        public PipeLineNode(int previousTask, int sourceTask) {
            this.previousTask = previousTask;
            this.sourceTask = sourceTask;
        }

        public PipeLineNode(int previousTask, int sourceTask, int targetTask) {
            this.sourceTask = sourceTask;
            this.targetTask = targetTask;
            this.previousTask = previousTask;
        }

        public String serialize() {
            StringBuilder sb = new StringBuilder("p: ").append(previousTask).append(", ");
            sb.append("s: ").append(sourceTask).append(", ");
            sb.append("m: ").append(inMemoryTasks).append(", ");
            sb.append("t: ").append(targetTask);
            return sb.toString();
        }
    }

    private List<PipeLineNode> nodes = new ArrayList<PipeLineNode>();

    public CommunicationPipeLine(Map conf, TreeSet<Integer> rootTaskId, TreeMap<String, TreeMap<Integer, TreeSet<Integer>>> mappings) {
        this.rootTaskIds = rootTaskId;
        buildPipeLine(mappings);

        StringBuilder sb = new StringBuilder();
        for (PipeLineNode n : nodes) {
            sb.append("[").append(n.serialize()).append("]");
        }
        LOG.info(sb.toString());
    }

    public void buildPipeLine(TreeMap<String, TreeMap<Integer, TreeSet<Integer>>> mappings) {
        rootNode = new PipeLineNode(-1);
        rootNode.inMemoryTasks.addAll(rootTaskIds);
        nodes.add(rootNode);

        PipeLineNode currentNode = rootNode;
        for (Map.Entry<String, TreeMap<Integer, TreeSet<Integer>>> e : mappings.entrySet()) {
            List<PipeLineNode> list = buildNodePipeLine(currentNode, e.getValue());
            nodes.addAll(list);
            if (list.size() > 0) {
                currentNode = list.get(list.size() - 1);
            }
        }
    }

    public List<PipeLineNode> buildNodePipeLine(PipeLineNode previousTask, TreeMap<Integer, TreeSet<Integer>> nodeMapping) {
        List<PipeLineNode> pipeLineNodes = new ArrayList<PipeLineNode>();
        PipeLineNode currentPreviousTask = previousTask;

        for (Map.Entry<Integer, TreeSet<Integer>> e : nodeMapping.entrySet()) {
            PipeLineNode pipeLineNode = new PipeLineNode(currentPreviousTask.sourceTask);
            pipeLineNodes.add(pipeLineNode);
            TreeSet<Integer> tasks = e.getValue();
            pipeLineNode.sourceTask = tasks.first();
            for (Integer t : tasks) {
                if (t != pipeLineNode.sourceTask) {
                    pipeLineNode.inMemoryTasks.add(t);
                }
            }
            // make sure last node in pipeline gets target as -1
            pipeLineNode.targetTask = -1;
            currentPreviousTask.targetTask = pipeLineNode.sourceTask;
            currentPreviousTask = pipeLineNode;
        }
        return pipeLineNodes;
    }

    public TreeSet<Integer> rootTasks() {
        return rootTaskIds;
    }

    public TreeSet<Integer> getAllTasks(int taskId) {
        TreeSet<Integer> returnTasks = new TreeSet<Integer>();
        PipeLineNode node = search(taskId);
        if (node != null) {
            LOG.info("Searched node with {}: " + node.serialize(), taskId);
            for (int t : node.inMemoryTasks) {
                if (t != taskId) {
                    returnTasks.add(t);
                }
            }
            // add the source as well
            returnTasks.add(node.sourceTask);
            // only valid targets are added, last node in pipe line
            if (node.targetTask >= 0) {
                returnTasks.add(node.targetTask);
            }
        } else {
            LOG.info("Failed to get node: " + taskId);
        }
        LOG.info("return tasks: {}", returnTasks);
        return returnTasks;
    }

    public TreeSet<Integer> getChildTasks(int taskId) {
        TreeSet<Integer> returnTasks = new TreeSet<Integer>();
        PipeLineNode node = search(taskId);
        if (node != null) {
            LOG.info("Searched node with {}: " + node.serialize(), taskId);
            if (taskId == node.sourceTask) {
                for (int t : node.inMemoryTasks) {
                    if (t != taskId) {
                        returnTasks.add(t);
                    }
                }
                // add the source as well
                returnTasks.add(node.sourceTask);
                // only valid targets are added, last node in pipe line
                if (node.targetTask >= 0) {
                    returnTasks.add(node.targetTask);
                }
            }
        } else {
            LOG.info("Failed to get node: " + taskId);
        }
        LOG.info("return tasks: {}", returnTasks);
        return returnTasks;
    }

    private PipeLineNode search(int taskId) {
        for (PipeLineNode node : nodes) {
            if (node.sourceTask == taskId || node.inMemoryTasks.contains(taskId)) {
                return node;
            }
        }
        return null;
     }
}