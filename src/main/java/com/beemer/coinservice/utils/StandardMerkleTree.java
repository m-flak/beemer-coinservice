package com.beemer.coinservice.utils;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StandardMerkleTree {

    private final byte[][] tree;
    private final byte[][] sortedLeaves;

    private StandardMerkleTree(byte[][] sortedLeaves) {
        this.sortedLeaves = sortedLeaves;
        this.tree = buildTree(sortedLeaves);
    }

    public static StandardMerkleTree of(List<byte[]> leafHashes) {
        byte[][] sorted = leafHashes.stream()
                .map(byte[]::clone)
                .sorted(StandardMerkleTree::compareBytes)
                .toArray(byte[][]::new);
        return new StandardMerkleTree(sorted);
    }

    @SuppressWarnings("rawtypes")
    public static byte[] standardLeafHash(List<Type> values) {
        byte[] abiEncoded = Numeric.hexStringToByteArray(FunctionEncoder.encodeConstructor(values));
        return Hash.sha3(Hash.sha3(abiEncoded));
    }

    public byte[] getRoot() {
        return tree[0].clone();
    }

    public List<byte[]> getProof(byte[] leafHash) {
        int leafIndex = findLeafIndex(leafHash);
        if (leafIndex < 0) {
            throw new IllegalArgumentException("Leaf not found in tree");
        }
        List<byte[]> proof = new ArrayList<>();
        int treeIndex = tree.length - 1 - leafIndex;
        while (treeIndex > 0) {
            proof.add(tree[siblingIndex(treeIndex)].clone());
            treeIndex = parentIndex(treeIndex);
        }
        return proof;
    }

    public static boolean verify(byte[] root, byte[] leafHash, List<byte[]> proof) {
        byte[] computed = leafHash.clone();
        for (byte[] sibling : proof) {
            computed = nodeHash(computed, sibling);
        }
        return Arrays.equals(computed, root);
    }

    private static byte[][] buildTree(byte[][] leaves) {
        if (leaves.length == 0) {
            throw new IllegalArgumentException("Expected non-zero number of leaves");
        }
        byte[][] tree = new byte[2 * leaves.length - 1][];
        for (int i = 0; i < leaves.length; i++) {
            tree[tree.length - 1 - i] = leaves[i];
        }
        for (int i = tree.length - 1 - leaves.length; i >= 0; i--) {
            tree[i] = nodeHash(tree[leftChildIndex(i)], tree[rightChildIndex(i)]);
        }
        return tree;
    }

    private static byte[] nodeHash(byte[] a, byte[] b) {
        byte[] left = compareBytes(a, b) <= 0 ? a : b;
        byte[] right = left == a ? b : a;
        byte[] combined = new byte[64];
        System.arraycopy(left, 0, combined, 0, 32);
        System.arraycopy(right, 0, combined, 32, 32);
        return Hash.sha3(combined);
    }

    private static int leftChildIndex(int i)  { return 2 * i + 1; }
    private static int rightChildIndex(int i) { return 2 * i + 2; }
    private static int parentIndex(int i)     { return (i - 1) / 2; }
    private static int siblingIndex(int i)    { return i % 2 == 0 ? i - 1 : i + 1; }

    private int findLeafIndex(byte[] leafHash) {
        for (int i = 0; i < sortedLeaves.length; i++) {
            if (Arrays.equals(sortedLeaves[i], leafHash)) return i;
        }
        return -1;
    }

    public record StandardMerkleTreeData(String format, List<String> tree, List<String> leaves) {}

    public StandardMerkleTreeData dump() {
        List<String> hexTree = Arrays.stream(tree).map(Numeric::toHexString).toList();
        List<String> hexLeaves = Arrays.stream(sortedLeaves).map(Numeric::toHexString).toList();
        return new StandardMerkleTreeData("standard-v1", hexTree, hexLeaves);
    }

    private static int compareBytes(byte[] a, byte[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int cmp = Byte.toUnsignedInt(a[i]) - Byte.toUnsignedInt(b[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length, b.length);
    }
}
