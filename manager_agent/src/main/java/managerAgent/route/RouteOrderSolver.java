package managerAgent.route;

import org.jgrapht.alg.tour.HeldKarpTSP;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import java.util.ArrayList;
import java.util.List;

public final class RouteOrderSolver {
    public record Cost(long seconds, long meters, String mode) {
        public Cost {
            if (seconds < 0 || meters < 0 || seconds > 1_000_000 || meters > 10_000_000)
                throw new IllegalArgumentException("路线成本无效");
        }
    }
    public record Solution(List<Integer> order, long beforeSeconds, long beforeMeters, long seconds, long meters) {}

    public static Solution solve(Cost[][] costs) {
        int n = costs.length;
        if (n < 2 || n > 8) throw new IllegalArgumentException("精确优化支持 2 至 8 个地点");
        long maxMeters = 0;
        for (int i = 0; i < n; i++) {
            if (costs[i] == null || costs[i].length != n) throw new IllegalArgumentException("路线成本矩阵不完整");
            for (int j = 0; j < n; j++) if (i != j) {
                if (costs[i][j] == null) throw new IllegalArgumentException("候选路段数据缺失，保留原顺序");
                maxMeters = Math.max(maxMeters, costs[i][j].meters());
            }
        }
        long factor = maxMeters * (n - 1) + 1;
        var graph = new DefaultDirectedWeightedGraph<Integer, DefaultWeightedEdge>(DefaultWeightedEdge.class);
        for (int i = 0; i <= n; i++) graph.addVertex(i);
        // A zero-cost dummy vertex turns an open path with free endpoints into a cycle.
        for (int i = 0; i <= n; i++) for (int j = 0; j <= n; j++) if (i != j) {
            var edge = graph.addEdge(i, j);
            graph.setEdgeWeight(edge, i == n || j == n ? 0 : costs[i][j].seconds() * factor + costs[i][j].meters());
        }
        var tour = new HeldKarpTSP<Integer, DefaultWeightedEdge>().getTour(graph);
        if (tour == null) throw new IllegalArgumentException("没有可行的完整路线");
        var vertices = tour.getVertexList().subList(0, n + 1);
        int dummy = vertices.indexOf(n);
        var order = new ArrayList<Integer>();
        for (int step = 1; step <= n; step++) order.add(vertices.get((dummy + step) % (n + 1)));
        var original = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) original.add(i);
        long[] before = total(original, costs), after = total(order, costs);
        if (before[0] == after[0] && before[1] == after[1]) order = original;
        return new Solution(List.copyOf(order), before[0], before[1], after[0], after[1]);
    }
    private static long[] total(List<Integer> order, Cost[][] costs) {
        long seconds = 0, meters = 0;
        for (int i = 1; i < order.size(); i++) {
            Cost cost = costs[order.get(i - 1)][order.get(i)];
            seconds += cost.seconds(); meters += cost.meters();
        }
        return new long[]{seconds, meters};
    }
}
