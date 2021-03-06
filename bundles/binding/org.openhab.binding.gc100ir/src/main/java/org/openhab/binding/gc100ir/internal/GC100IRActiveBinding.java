/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.gc100ir.internal;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.openhab.binding.gc100ir.GC100IRBindingProvider;
import org.openhab.binding.gc100ir.util.GC100ItemBean;
import org.openhab.binding.gc100ir.util.ItemUtility;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This refresh service for the GC100 binding is used to periodically check to
 * ensure all GC100 sockets are still open and alive.
 * 
 * All item commands are sent via the tcp socket
 * 
 * @author Parikshit Thakur & Team
 * @since 1.6.0
 * 
 */
public class GC100IRActiveBinding extends
		AbstractActiveBinding<GC100IRBindingProvider> implements ManagedService {

	private static final Logger logger = LoggerFactory
			.getLogger(GC100IRActiveBinding.class);

	private Map<String, GC100IRConnector> connectors = new HashMap<String, GC100IRConnector>();
	private Map<String, GC100IRHost> nameHostMapper = null;

	/**
	 * the refresh interval which is used to check for lost connections
	 * (optional, defaults to 60000ms)
	 */
	private long refreshInterval = 60000;

	public void activate() {
		logger.debug(getName() + " activate()");
		setProperlyConfigured(true);
	}

	public void deactivate() {
		logger.debug(getName() + " deactivate()");

		// close any open connections
		for (GC100IRConnector connector : connectors.values()) {
			if (connector.isConnected()) {
				connector.close();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected long getRefreshInterval() {
		return refreshInterval;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getName() {
		return "GC100IR Refresh Service";
	}

	/**
	 * @{inheritDoc
	 */
	@Override
	public void bindingChanged(BindingProvider provider, String itemName) {
		if (provider instanceof GC100IRBindingProvider) {
			GC100IRBindingProvider gc100Provider = (GC100IRBindingProvider) provider;
			registerWatch(gc100Provider, itemName);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void allBindingsChanged(BindingProvider provider) {
		if (provider instanceof GC100IRBindingProvider) {
			GC100IRBindingProvider gc100Provider = (GC100IRBindingProvider) provider;
			for (String itemName : gc100Provider.getItemNames()) {
				registerWatch(gc100Provider, itemName);
			}
		}
	}
	/**
	 * Registers all items and GC100IRConnector.
	 */
	private void registerAllWatches() {
		for (BindingProvider provider : providers) {
			if (provider instanceof GC100IRBindingProvider) {
				GC100IRBindingProvider gc100Provider = (GC100IRBindingProvider) provider;
				for (String itemName : gc100Provider.getItemNames()) {
					registerWatch(gc100Provider, itemName);
				}
			}
		}
	}
	/**
	 * Register each item and adds them in ItemUtility list.
	 * 
	 * @param gc100Provider
	 * @param itemName
	 */
	private void registerWatch(GC100IRBindingProvider gc100Provider,
			String itemName) {

		if (!gc100Provider.providesBindingFor(itemName)) {
			return;
		}

		String gc100Instance = gc100Provider.getGC100Instance(itemName);
		int module = gc100Provider.getGC100Module(itemName);
		int connector = gc100Provider.getGC100Connector(itemName);
		String code = gc100Provider.getCode(itemName);

		GC100ItemBean itemBean = new GC100ItemBean();
		itemBean.setGC100Instance(gc100Instance);
		itemBean.setModule(module);
		itemBean.setConnector(connector);
		itemBean.setCode(code);

		ItemUtility itemUtility = ItemUtility.getInstance();
		itemUtility.addItem(itemName, itemBean);

		GC100IRConnector gc100Connector = getGC100Connector(gc100Instance);

		if (gc100Connector == null) {
			logger.error("Connection failed with " + gc100Instance);
		}
	}
	/**
	 * Gets the GC100Instance according to item name.
	 * 
	 * @param itemName
	 * @return a String value of GC100 instance.
	 */
	private String getGC100Instance(String itemName) {
		for (BindingProvider provider : providers) {
			if (provider instanceof GC100IRBindingProvider) {
				GC100IRBindingProvider gc100Provider = (GC100IRBindingProvider) provider;
				if (gc100Provider.getItemNames().contains(itemName)) {
					return gc100Provider.getGC100Instance(itemName);
				}
			}
		}
		return null;
	}

	/**
	 * Gets the GC100 connector object from connector list.
	 * 
	 * @param gc100Instance
	 * @return GC100IRConnector instance.
	 */
	private GC100IRConnector getGC100Connector(String gc100Instance) {
		// sanity check
		if (gc100Instance == null)
			return null;

		// check if the connector for this instance already exists
		GC100IRConnector connector = connectors.get(gc100Instance);
		if (connector != null)
			return connector;

		GC100IRHost gc100Host;
		if (gc100Instance.startsWith("#")) {
			// trim off the '#' identifier
			String instance = gc100Instance.substring(1);

			// check if we have been initialised yet - can't process
			// named instances until we have read the binding config
			if (nameHostMapper == null) {
				logger.trace(
						"Attempting to access the named instance '{}' before the binding config has been loaded",
						instance);
				return null;
			}

			// check this instance name exists in our config
			if (!nameHostMapper.containsKey(instance)) {
				logger.error(
						"Named instance '{}' does not exist in the binding config",
						instance);
				return null;
			}

			gc100Host = nameHostMapper.get(instance);
		} else {
			gc100Host = new GC100IRHost();
			gc100Host.setHostname(gc100Instance);
		}

		// create a new connection handler
		logger.debug("Creating new GC100IRConnector for '{}' on {}",
				gc100Instance, gc100Host.getHostname());
		connector = new GC100IRConnector(gc100Host, eventPublisher);
		connectors.put(gc100Instance, connector);

		// attempt to open the connection straight away
		try {
			connector.open();
		} catch (Exception e) {
			logger.error("Connection failed for '{}' on {}", gc100Instance,
					gc100Host.getHostname());
		}

		return connector;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void execute() {
		for (Map.Entry<String, GC100IRConnector> entry : connectors.entrySet()) {
			GC100IRConnector connector = entry.getValue();
			if (connector.isConnected()) {
				// we are still connected but send a ping to make sure
				if (connector.ping())
					continue;

				// broken connection so attempt to reconnect
				logger.debug(
						"Broken connection found for '{}', attempting to reconnect...",
						entry.getKey());
				try {
					connector.open();
				} catch (Exception e) {
					logger.debug(
							"Reconnect failed for '{}', will retry in {}s",
							entry.getKey(), refreshInterval / 1000);
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalReceiveCommand(String itemName, Command command) {

		try {
			// lookup the GC100 instance name and property for this item
			String gc100Instance = getGC100Instance(itemName);

			GC100IRConnector connector = getGC100Connector(gc100Instance);

			if (connector == null) {
				logger.warn(
						"Received command ({}) for item {} but no GC100 connector found for {}, ignoring",
						command.toString(), itemName, gc100Instance);
				return;
			}
			if (!connector.isConnected()) {
				logger.warn(
						"Received command ({}) for item {} but the connection to the GC100 instance {} is down, ignoring",
						command.toString(), itemName, gc100Instance);
				return;
			}
			connector.invokeCommand(itemName);

		} catch (Exception e) {
			logger.error("Error handling command", e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalReceiveUpdate(String itemName, State newState) {
		logger.info("Received update from Item: " + itemName
				+ " with new Value: " + newState);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updated(Dictionary<String, ?> config)
			throws ConfigurationException {
		logger.debug(getName() + " updated()");

		Map<String, GC100IRHost> hosts = new HashMap<String, GC100IRHost>();

		Enumeration<String> keys = config.keys();

		while (keys.hasMoreElements()) {
			String key = keys.nextElement();

			if ("service.pid".equals(key)) {
				continue;
			}

			String[] parts = key.split("\\.");
			String hostname = parts[0];

			GC100IRHost host = hosts.get(hostname);
			if (host == null) {
				host = new GC100IRHost();
			}

			String value = ((String) config.get(key)).trim();

			if ("host".equals(parts[1])) {
				host.setHostname(value);
			}
			hosts.put(hostname, host);
		}

		nameHostMapper = hosts;
		registerAllWatches();
	}
}
