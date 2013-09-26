package be.kuleuven.cs.gridlock.gui.map.layered;

import be.kuleuven.cs.gridlock.geo.coordinates.Coordinates;
import be.kuleuven.cs.gridlock.gui.map.PixelMapper;
import be.kuleuven.cs.gridlock.simulation.SimulationContext;
import be.kuleuven.cs.gridlock.simulation.api.LinkReference;
import be.kuleuven.cs.gridlock.simulation.api.NodeReference;
import be.kuleuven.cs.gridlock.simulation.events.Event;
import be.kuleuven.cs.gridlock.simulation.events.EventFilter;
import be.kuleuven.cs.gridlock.simulation.events.EventListener;
import be.kuleuven.cs.gridlock.utilities.graph.Graph;
import be.kuleuven.cs.gridlock.utilities.graph.Node;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class for representing a graphical representation of nodes.
 * @author Kristof Coninx <kristof.coninx at student.kuleuven.be>
 */
public class NodeLayer extends NetworkLayer {

    private Stroke stroke = new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private Color color = Color.BLUE;
    private int width;

    public NodeLayer(SimulationContext context, Graph<NodeReference, LinkReference> graph) {
        super(graph, false);
        this.width = 6;
    }

    @Override
    public void paintLayer(PixelMapper mapper, Graphics2D graphics) {
        for (NodeReference vertex : getGraph().getNodes()) {
            this.paintVertex(vertex, mapper, graphics);
        }
    }

    private void paintVertex(NodeReference vertex, PixelMapper mapper, Graphics2D graphics) {
        List<Coordinates> coordinates;
        Node<NodeReference, LinkReference> node = getGraph().getNode(vertex);
        coordinates = Arrays.asList(node.getAnnotation("location",Coordinates.class));

        int[][] points = doCorrection(mapCoordinates(coordinates, mapper), vertex);
        graphics.setColor(chooseColor(vertex));
        graphics.setStroke(chooseStroke(vertex));
        graphics.drawOval(points[0][0] - this.getWidth() / 2, points[1][0] - this.getWidth() / 2, this.getWidth(), this.getWidth());
    }

    protected Color chooseColor(NodeReference vertex) {
        return this.getColor();
    }

    protected Stroke chooseStroke(NodeReference vertex) {
        return this.getStroke();
    }

    /**
     * @return the stroke
     */
    @Override
    protected Stroke getStroke() {
        return stroke;
    }

    /**
     * @return the color
     */
    @Override
    protected Color getColor() {
        return color;
    }

    /**
     * @return the width
     */
    protected int getWidth() {
        return width;
    }

    protected int[][] doCorrection(int[][] mapCoordinates, NodeReference vertex) {
        return mapCoordinates;
    }

    public static class ElectricalNodeLayer extends NodeLayer implements EventListener, EventFilter {

        private Map<Long, Integer> nodeLoad;
        private Set<Long> stations;
        private int maxLoad;
        private int markGreen;
        private int markYellow;
        private int markRed;
        private Stroke smallstroke;

        public ElectricalNodeLayer(SimulationContext context, Graph<NodeReference, LinkReference> graph) {
            super(context, graph);
            this.nodeLoad = new HashMap<Long, Integer>();
            this.stations = new HashSet<Long>();
            this.maxLoad = 0;
            this.markGreen = 0;
            this.markYellow = 0;
            this.markRed = 0;
            this.smallstroke = new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            context.getEventController().registerListener(this, this);
            this.parseGraphForStations();
        }

        @Override
        public boolean pass(Event event) {
            return event.getType().startsWith("infrastructure:chargingstation");
        }

        @Override
        public void notifyOf(Event event) {
            Long stationID = event.getAttribute("station", Long.class);
            int clients = event.getAttribute("clients", Integer.class);
            this.nodeLoad.put(stationID, clients);
            if (clients > maxLoad) {
                recalculateValues(clients);
            }
        }

        @Override
        public void paintLayer(PixelMapper mapper, Graphics2D graphics) {
            super.paintLayer(mapper, graphics);
            String max = "Max clients at station: " + new Integer(this.maxLoad).toString();
            graphics.setColor(Color.WHITE);
            graphics.drawChars(max.toCharArray(), 0, max.length(), 100, 20);
        }

        @Override
        protected Color chooseColor(NodeReference vertex) {
            if (!this.stations.contains(vertex.getId())) {
                return Color.white;
            }
            if (!this.nodeLoad.containsKey(vertex.getId())) {
                return getColor();
            } else {
                int load = this.nodeLoad.get(vertex.getId());
                if (load <= markRed) {
                    if (load <= markYellow) {
                        if (load <= markGreen) {
                            return new Color(0, 255, 0, 200);
                        }
                        return new Color(255, 255, 0, 200);
                    }
                    return new Color(255, 0, 0, 200);
                }
            }
            return new Color(0, 0, 0, 200);
        }

        @Override
        protected Stroke chooseStroke(NodeReference vertex) {
            if (!this.nodeLoad.containsKey(vertex.getId())) {
                return this.smallstroke;
            }
            return super.chooseStroke(vertex);
        }

        private void recalculateValues(int clients) {
            maxLoad = clients;
            int division = this.maxLoad / 3;
            this.markGreen = division * 1;
            this.markYellow = division * 2;
            this.markRed = division * 3;
        }

        private void parseGraphForStations() {
            for (NodeReference ref : this.getGraph().getNodes()) {
                boolean station = (boolean) getGraph().getNode(ref).getAnnotation("station", false);
                if (station) {
                    this.stations.add(ref.getId());
                }
            }
        }

        @Override
        protected int[][] doCorrection(int[][] mapCoordinates, NodeReference vertex) {
            if (this.stations.contains(vertex.getId())) {
                if (vertex.getId() % 2 == 0) {
                    mapCoordinates[0][0] = mapCoordinates[0][0] - 5;
                    mapCoordinates[1][0] = mapCoordinates[1][0] - 5;
                } else {
                    mapCoordinates[0][0] = mapCoordinates[0][0] + 5;
                    mapCoordinates[1][0] = mapCoordinates[1][0] + 5;
                }

            }
            return mapCoordinates;
        }
    }

    public static class LabeledNodeLayer extends NodeLayer {

        public LabeledNodeLayer(SimulationContext context, Graph<NodeReference, LinkReference> graph) {
            super(context, graph);
        }

        @Override
        public void paintLayer(PixelMapper mapper, Graphics2D graphics) {
            for (NodeReference vertex : getGraph().getNodes()) {
                this.paintVertex(vertex, mapper, graphics);
            }
        }

        private void paintVertex(NodeReference vertex, PixelMapper mapper, Graphics2D graphics) {
            List<Coordinates> coordinates;
            Node<NodeReference, LinkReference> node = getGraph().getNode(vertex);
            coordinates = Arrays.asList(node.getAnnotation("location",Coordinates.class));
            String id = vertex.getId().toString();

            int[][] points = mapCoordinates(coordinates, mapper);
            graphics.setColor(Color.WHITE);
            graphics.setStroke(getStroke());
            graphics.drawString(id, points[0][0], points[1][0] + 20);

        }
    }
}
