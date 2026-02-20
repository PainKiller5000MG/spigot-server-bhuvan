package net.minecraft.world.level.pathfinder;

import java.util.Arrays;

public class BinaryHeap {

    private Node[] heap = new Node[128];
    private int size;

    public BinaryHeap() {}

    public Node insert(Node node) {
        if (node.heapIdx >= 0) {
            throw new IllegalStateException("OW KNOWS!");
        } else {
            if (this.size == this.heap.length) {
                Node[] anode = new Node[this.size << 1];

                System.arraycopy(this.heap, 0, anode, 0, this.size);
                this.heap = anode;
            }

            this.heap[this.size] = node;
            node.heapIdx = this.size;
            this.upHeap(this.size++);
            return node;
        }
    }

    public void clear() {
        this.size = 0;
    }

    public Node peek() {
        return this.heap[0];
    }

    public Node pop() {
        Node node = this.heap[0];

        this.heap[0] = this.heap[--this.size];
        this.heap[this.size] = null;
        if (this.size > 0) {
            this.downHeap(0);
        }

        node.heapIdx = -1;
        return node;
    }

    public void remove(Node node) {
        this.heap[node.heapIdx] = this.heap[--this.size];
        this.heap[this.size] = null;
        if (this.size > node.heapIdx) {
            if (this.heap[node.heapIdx].f < node.f) {
                this.upHeap(node.heapIdx);
            } else {
                this.downHeap(node.heapIdx);
            }
        }

        node.heapIdx = -1;
    }

    public void changeCost(Node node, float newCost) {
        float f1 = node.f;

        node.f = newCost;
        if (newCost < f1) {
            this.upHeap(node.heapIdx);
        } else {
            this.downHeap(node.heapIdx);
        }

    }

    public int size() {
        return this.size;
    }

    private void upHeap(int idx) {
        Node node = this.heap[idx];

        int j;

        for (float f = node.f; idx > 0; idx = j) {
            j = idx - 1 >> 1;
            Node node1 = this.heap[j];

            if (f >= node1.f) {
                break;
            }

            this.heap[idx] = node1;
            node1.heapIdx = idx;
        }

        this.heap[idx] = node;
        node.heapIdx = idx;
    }

    private void downHeap(int idx) {
        Node node = this.heap[idx];
        float f = node.f;

        while (true) {
            int j = 1 + (idx << 1);
            int k = j + 1;

            if (j >= this.size) {
                break;
            }

            Node node1 = this.heap[j];
            float f1 = node1.f;
            Node node2;
            float f2;

            if (k >= this.size) {
                node2 = null;
                f2 = Float.POSITIVE_INFINITY;
            } else {
                node2 = this.heap[k];
                f2 = node2.f;
            }

            if (f1 < f2) {
                if (f1 >= f) {
                    break;
                }

                this.heap[idx] = node1;
                node1.heapIdx = idx;
                idx = j;
            } else {
                if (f2 >= f) {
                    break;
                }

                this.heap[idx] = node2;
                node2.heapIdx = idx;
                idx = k;
            }
        }

        this.heap[idx] = node;
        node.heapIdx = idx;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public Node[] getHeap() {
        return (Node[]) Arrays.copyOf(this.heap, this.size);
    }
}
