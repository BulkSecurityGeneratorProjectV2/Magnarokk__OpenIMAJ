package org.openimaj.rdf.storm.topology.bolt;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.openimaj.rdf.storm.bolt.RETEStormNode;
import scala.actors.threadpool.Arrays;

import backtype.storm.topology.IRichBolt;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.mem.GraphMem;
import com.hp.hpl.jena.reasoner.TriplePattern;
import com.hp.hpl.jena.reasoner.rulesys.Functor;
import com.hp.hpl.jena.reasoner.rulesys.Rule;
import com.hp.hpl.jena.reasoner.rulesys.impl.RETEClauseFilter;
import com.hp.hpl.jena.reasoner.rulesys.impl.RETERuleContext;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;

/**
 * A Storm {@link IRichBolt} which encapsulates the functionality of
 * {@link RETEClauseFilter} instances
 *
 * @author David Monks <dm11g08@ecs.soton.ac.uk>, Sina Samangooei <ss@ecs.soton.ac.uk>
 *
 */
public class FlexibleReteFilterBolt extends FlexibleReteBolt {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2941229666460288498L;
	protected final static Logger logger = Logger.getLogger(ReteFilterBolt.class);
	/**
	 *
	 */
	private TriplePattern filter;

	/**
	 * This filter holds a {@link Rule}.
	 * @param rule
	 */
	public FlexibleReteFilterBolt(Rule rule) {
		super(rule);
	}

	@Override
	public void execute(Tuple input) {
		boolean isAdd = input.getBooleanByField(BASE_FIELDS[IS_ADD]);
		@SuppressWarnings("unchecked")
		List<String> outFields = Arrays.asList(this.getVars());
		if(logger.isDebugEnabled()){
			logger.debug(String.format("Executing: %s",filter));
		}
		// Extract Jena Graph from Storm Tuple
		Graph graph = asGraph(input);
		// Extract Triples that match this Filter's pattern
		ExtendedIterator<Triple> it = graph.find(filter.asTripleMatch());
		// With each valid triple...
		while (it.hasNext()){
			Triple t = it.next();
			logger.debug(String.format("Filter passed triple: %s",t));
			
			// Create an array of Objects of equal size to the number of expected variables,
			// plus all base ReteStorm fields (graph, isAdd, timestamp, etc, as defined in
			// FlexibleReteBolt)
			Object[] vals = new Object[this.getVars().length];
			
			// For each part of the triple, check if the Pattern declares it to be variable
			// (or a functor, in the case of Objects)
			if (filter.getSubject().isVariable())
				// if it is a variable, insert its value into the array of to-be-Values
				vals[outFields.indexOf(filter.getSubject().getName())] = t.getSubject();
			
			if (filter.getPredicate().isVariable())
				vals[outFields.indexOf(filter.getPredicate().getName())] = t.getPredicate();
			
			if (filter.getObject().isVariable())
				vals[outFields.indexOf(filter.getObject().getName())] = t.getObject();
			else if (filter.getObject().isLiteral() && filter.getObject().getLiteralValue() instanceof Functor){
				// if the object is a functor, check each node in the functor to see if it is a variable
				Functor f = (Functor)filter.getObject().getLiteralValue();
				for (int i = 0; i < f.getArgs().length; i++){
					Node n = f.getArgs()[i];
					if (n.isVariable())
						// if it is, insert its value into the array of to-be-Values
						vals[outFields.indexOf(n.getName())] = ((Functor)t.getObject().getLiteralValue()).getArgs()[i];
				}
			}
			
			// insert this Tuple's value of isAdd to be passed onto subscribing Bolts.
			vals[outFields.indexOf(BASE_FIELDS[IS_ADD])] = isAdd;
			
			// in case this Triple has been extracted from a larger graph, create a new Graph
			// containing just this Triple.
			Graph g = new GraphMem();
			g.add(t);
			// insert the new graph into the array of to-be-Values
			vals[outFields.indexOf(BASE_FIELDS[GRAPH])] = g;
			
			// create a new Values instance and populate it from the array of to-be-Values,
			// thereby ensuring that the correct values end up mapped to the correct fields.
			Values values = new Values();
			for (Object o : vals)
				values.add(o);
			
			// fire the new values
			fire(values,isAdd);
			// emit using the input Tuple as an anchor
			emit(input);
		}
		// Once all valid triples are extracted and fired individually, acknowledge the input tuple.
		acknowledge(input);
	}

	@Override
	public void prepare() {
		this.filter = (TriplePattern) this.getRule().getBodyElement(0);
	}

	@Override
	public RETEStormNode clone(Map<RETEStormNode, RETEStormNode> netCopy,
			RETERuleContext context) {
		// TODO
		return null;
	}

}