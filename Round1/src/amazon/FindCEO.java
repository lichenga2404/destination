package amazon;

import java.util.ArrayList;
import java.util.List;

public class FindCEO {

	public static void main(String[] args) {
		List<Employee> employees = new ArrayList<Employee>();
		Employee e1 = new Employee(1, 3);
		Employee e2 = new Employee(2, 1);
		Employee e3 = new Employee(3, 5);
		Employee e4 = new Employee(4, 5);
		Employee e5 = new Employee(5, 6);
		Employee e6 = new Employee(6, 6);
		
		employees.add(e1);
		employees.add(e2);
		employees.add(e3);
		employees.add(e4);
		employees.add(e5);
		employees.add(e6);
		
		UnionFind uf = new UnionFind(employees.size());
		for (Employee e : employees) {
			uf.union(e.selfId, e.bossId);
		}
		
		System.out.println(uf.find(employees.get(0).bossId));
	}
}
class Employee {
	int selfId;
	int bossId;
	
	public Employee(int selfId, int bossId) {
		this.selfId = selfId;
		this.bossId = bossId;
	}
}

class UnionFind {
	int[] parent = new int[1000];
	
	UnionFind(int size) {
		for (int i = 0; i < parent.length; i++) {
			parent[i] = i;
		}
	}
	
	void union(int id1, int id2) {
		int root1 = find(id1);
		int root2 = find(id2);
		
		if (root1 != root2) {
			parent[root1] = root2;
		}
	}
	
	int find(int id) {
		if (parent[id] == id) {
			return id;
		}
		
//		parent[id] = find(parent[id]);
//		return parent[id];
		return find(parent[id]);// This is good enough
	}
}