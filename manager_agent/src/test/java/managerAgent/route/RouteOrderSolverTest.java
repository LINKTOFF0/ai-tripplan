package managerAgent.route;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RouteOrderSolverTest {
    private RouteOrderSolver.Cost[][] matrix(int n, long seconds, long meters) {
        var matrix = new RouteOrderSolver.Cost[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) if (i != j)
            matrix[i][j] = new RouteOrderSolver.Cost(seconds, meters, "walking");
        return matrix;
    }
    @Test void findsOpenPathWithDifferentStartAndEnd() {
        var costs = matrix(4, 100, 100);
        costs[2][0] = new RouteOrderSolver.Cost(1, 1000, "walking");
        costs[0][3] = new RouteOrderSolver.Cost(1, 1000, "walking");
        costs[3][1] = new RouteOrderSolver.Cost(1, 1000, "walking");
        var result = RouteOrderSolver.solve(costs);
        assertEquals(List.of(2, 0, 3, 1), result.order());
        assertEquals(3, result.seconds());
        assertEquals(3000, result.meters());
        assertEquals(300, result.beforeSeconds());
    }
    @Test void breaksTimeTiesByDistanceAndKeepsOriginalOnEqualCost() {
        var costs = matrix(3, 10, 100);
        assertEquals(List.of(0, 1, 2), RouteOrderSolver.solve(costs).order());
        costs[1][0] = new RouteOrderSolver.Cost(10, 1, "walking");
        costs[0][2] = new RouteOrderSolver.Cost(10, 1, "walking");
        assertEquals(List.of(1, 0, 2), RouteOrderSolver.solve(costs).order());
    }
    @Test void rejectsPartialMatricesAndOutOfRangeCosts() {
        var costs = matrix(3, 10, 100); costs[2][1] = null;
        assertThrows(IllegalArgumentException.class, () -> RouteOrderSolver.solve(costs));
        assertThrows(IllegalArgumentException.class, () -> RouteOrderSolver.solve(matrix(9, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new RouteOrderSolver.Cost(-1, 1, "walking"));
    }
    @Test void agreesWithAllPermutationsOnAsymmetricFourPlaceGraph() {
        var costs = matrix(4, 0, 0);
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) if (i != j)
            costs[i][j] = new RouteOrderSolver.Cost((i * 17 + j * 13) % 21 + 1, (i * 7 + j * 11) % 16 + 1, "walking");
        long bestSeconds = Long.MAX_VALUE, bestMeters = Long.MAX_VALUE;
        for (int a = 0; a < 4; a++) for (int b = 0; b < 4; b++) for (int c = 0; c < 4; c++) for (int d = 0; d < 4; d++) {
            if (a == b || a == c || a == d || b == c || b == d || c == d) continue;
            long seconds = costs[a][b].seconds() + costs[b][c].seconds() + costs[c][d].seconds();
            long meters = costs[a][b].meters() + costs[b][c].meters() + costs[c][d].meters();
            if (seconds < bestSeconds || seconds == bestSeconds && meters < bestMeters) { bestSeconds = seconds; bestMeters = meters; }
        }
        var result = RouteOrderSolver.solve(costs);
        assertEquals(bestSeconds, result.seconds()); assertEquals(bestMeters, result.meters());
    }
}
