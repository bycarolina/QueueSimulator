import java.text.DecimalFormat;
import java.util.*;

public class QueueSimT1 {
    static class LCG {
        private final long a, c, m, x0;
        private long x;
        private long count;
        LCG(long seed, long a, long c, long m) {
            this.a = a; this.c = c; this.m = m; this.x0 = seed; this.x = seed; this.count = 0L;
        }
        LCG() { this(5L, 1664525L, 1013904223L, 1L << 32); }
        double next01() { x = (a * x + c) % m; count++; return (x / (double) m); }
        double uniform(double min, double max) { return min + (max - min) * next01(); }
        long used() { return count; }
    }

    enum EType { ARRIVAL, DEPARTURE }
    static class Event implements Comparable<Event> {
        final double t; final EType type; final String qid;
        Event(double t, EType type, String qid) { this.t = t; this.type = type; this.qid = qid; }
        public int compareTo(Event o) { return Double.compare(this.t, o.t); }
    }

    static class QueueNode {
        final String id;
        final int servers;
        final int capacity;
        final double arrMin, arrMax;
        final double svcMin, svcMax;
        int inSystem = 0;
        int busyServers = 0;
        int lost = 0;
        double lastUpdate = 0.0;
        final Map<Integer, Double> stateTime = new HashMap<Integer, Double>();
        QueueNode(String id, int servers, int capacity,
                  double arrMin, double arrMax,
                  double svcMin, double svcMax) {
            this.id = id; this.servers = servers; this.capacity = capacity;
            this.arrMin = arrMin; this.arrMax = arrMax;
            this.svcMin = svcMin; this.svcMax = svcMax;
            for (int i = 0; i <= capacity; i++) stateTime.put(i, 0.0);
        }
        void updateStateTo(double t) {
            double dt = t - lastUpdate;
            if (dt < 0) dt = 0;
            double cur = stateTime.containsKey(inSystem) ? stateTime.get(inSystem) : 0.0;
            stateTime.put(inSystem, cur + dt);
            lastUpdate = t;
        }
    }

    static class Route {
        final String from, to; final double p;
        Route(String from, String to, double p) { this.from = from; this.to = to; this.p = p; }
    }

    static class QueueNetwork {
        final Map<String, QueueNode> nodes = new LinkedHashMap<String, QueueNode>();
        final Map<String, List<Route>> routesFrom = new HashMap<String, List<Route>>();
        final PriorityQueue<Event> agenda = new PriorityQueue<Event>();
        final LCG rng = new LCG();
        double globalClock = 0.0;
        double startAt = 1.5;
        long limitRandoms = 100000L;
        boolean stop = false;

        void addNode(QueueNode q) { nodes.put(q.id, q); }
        void addRoute(String from, String to, double p) {
            List<Route> list = routesFrom.get(from);
            if (list == null) {
                list = new ArrayList<Route>();
                routesFrom.put(from, list);
            }
            list.add(new Route(from, to, p));
        }
        void schedule(double t, EType type, String qid) { agenda.add(new Event(t, type, qid)); }

        void init() {
            for (QueueNode q : nodes.values()) {
                if (q.arrMin > 0 || q.arrMax > 0) {
                    schedule(startAt, EType.ARRIVAL, q.id);
                }
            }
        }

        void run() {
            init();
            while (!agenda.isEmpty() && !stop) {
                Event e = agenda.poll();
                globalClock = e.t;
                for (QueueNode q : nodes.values()) q.updateStateTo(globalClock);
                if (e.type == EType.ARRIVAL) processArrival(nodes.get(e.qid));
                else processDeparture(nodes.get(e.qid));
            }
        }

        void processArrival(QueueNode q) {
            if (q.inSystem < q.capacity) {
                q.inSystem++;
                if (q.busyServers < q.servers) {
                    q.busyServers++;
                    if (rng.used() < limitRandoms) {
                        double svc = rng.uniform(q.svcMin, q.svcMax);
                        if (rng.used() >= limitRandoms) stop = true;
                        schedule(globalClock + svc, EType.DEPARTURE, q.id);
                    } else { stop = true; return; }
                }
            } else {
                q.lost++;
            }
            if (q.arrMin > 0 || q.arrMax > 0) {
                if (rng.used() < limitRandoms) {
                    double inter = rng.uniform(q.arrMin, q.arrMax);
                    if (rng.used() >= limitRandoms) stop = true;
                    schedule(globalClock + inter, EType.ARRIVAL, q.id);
                } else { stop = true; return; }
            }
        }

        void processDeparture(QueueNode q) {
            if (q.inSystem > 0) {
                q.inSystem--;
                if (q.inSystem >= q.servers) {
                    if (rng.used() < limitRandoms) {
                        double svc = rng.uniform(q.svcMin, q.svcMax);
                        if (rng.used() >= limitRandoms) stop = true;
                        schedule(globalClock + svc, EType.DEPARTURE, q.id);
                    } else { stop = true; return; }
                } else {
                    q.busyServers--;
                }
            }
            Route r = pickRoute(q.id);
            if (r != null) {
                if ("EXIT".equals(r.to)) return;
                QueueNode dest = nodes.get(r.to);
                if (dest == null) return;
                if (dest.inSystem < dest.capacity) {
                    dest.inSystem++;
                    if (dest.busyServers < dest.servers) {
                        dest.busyServers++;
                        if (rng.used() < limitRandoms) {
                            double svc = rng.uniform(dest.svcMin, dest.svcMax);
                            if (rng.used() >= limitRandoms) stop = true;
                            schedule(globalClock + svc, EType.DEPARTURE, dest.id);
                        } else { stop = true; return; }
                    }
                } else {
                    dest.lost++;
                }
            }
        }

        Route pickRoute(String from) {
            List<Route> outs = routesFrom.get(from);
            if (outs == null || outs.isEmpty()) return null;
            if (rng.used() < limitRandoms) {
                double u = rng.next01();
                if (rng.used() >= limitRandoms) stop = true;
                double acc = 0.0;
                for (Route r : outs) {
                    acc += r.p;
                    if (u <= acc) return r;
                }
                return outs.get(outs.size() - 1);
            } else { stop = true; return null; }
        }

        void printReport() {
            DecimalFormat d2 = new DecimalFormat("0.00");
            DecimalFormat d4 = new DecimalFormat("0.0000");
            System.out.println("Tempo Global da Simulação: " + d2.format(globalClock));
            System.out.println("Aleatórios utilizados: " + rng.used());
            for (QueueNode q : nodes.values()) {
                System.out.println("\nFila: " + q.id + "  (Servidores=" + q.servers + ", Capacidade=" + q.capacity +
                        ", Chegadas=[" + q.arrMin + "," + q.arrMax + "], Serviço=[" + q.svcMin + "," + q.svcMax + "]) ");
                System.out.println("Perdas: " + q.lost);
                System.out.println("+---------+------------------+------------------+");
                System.out.println("| Estado  | Tempo Acumulado  | Probabilidade    |");
                System.out.println("+---------+------------------+------------------+");
                for (int n = 0; n <= q.capacity; n++) {
                    double tAccum = q.stateTime.getOrDefault(n, 0.0);
                    double prob = (globalClock > 0) ? tAccum / globalClock : 0.0;
                    System.out.printf("| %-7d| %16s | %16s |%n", n, d2.format(tAccum), d4.format(prob));
                }
                System.out.println("+---------+------------------+------------------+");
            }
        }
    }

    public static void main(String[] args) {
        QueueNetwork net = new QueueNetwork();
        net.startAt = 2.0;
        net.limitRandoms = 100000L;

        // F1: G/G/1/5, chegadas [2,4], serviço [1,2]
        net.addNode(new QueueNode("F1", 1, 5, 2, 4, 1, 2));
        // F2: G/G/2/5, serviço [4,6]
        net.addNode(new QueueNode("F2", 2, 5, 0, 0, 4, 6));
        // F3: G/G/2/10, serviço [5,15]
        net.addNode(new QueueNode("F3", 2, 10, 0, 0, 5, 15));

        net.addRoute("F1", "F2", 0.8);
        net.addRoute("F1", "F3", 0.2);

        net.addRoute("F2", "F1", 0.3);
        net.addRoute("F2", "F2", 0.5);
        net.addRoute("F2", "EXIT", 0.2);

        net.addRoute("F3", "F1", 0.7);
        net.addRoute("F3", "EXIT", 0.3);

        net.run();
        net.printReport();
    }
}