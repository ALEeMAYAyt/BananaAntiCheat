package it.aleemayayt.bananaanticheat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;

public class BananaAntiCheat extends JavaPlugin implements Listener, TabExecutor {

    private ConfigManager configManager;
    private ProtocolManager protocolManager;

    // Per-player state
    private final Map<UUID, PlayerData> dataMap = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        configManager.load(); // Carica tutte le impostazioni

        // register bukkit listeners
        Bukkit.getPluginManager().registerEvents(this, this);

        // ProtocolLib packet listener
        try {
            protocolManager = ProtocolLibrary.getProtocolManager();
            protocolManager.addPacketListener(new ProtocolListener(this));
            getLogger().info("[BananaAntiCheat] ProtocolLib listener aggiunto.");
        } catch (Throwable t) {
            getLogger().warning("[BananaAntiCheat] ProtocolLib non disponibile o errore inizializzazione: " + t.getMessage());
        }

        // command
        getCommand("banana").setExecutor(this);

        getLogger().info(ChatColor.translateAlternateColorCodes('&', configManager.getPrefix()) + " abilitato.");
    }

    @Override
    public void onDisable() {
        // clean-up
        try {
            if (protocolManager != null) {
                protocolManager.removePacketListeners(this);
            }
        } catch (Throwable ignored) {}
        getLogger().info(ChatColor.translateAlternateColorCodes('&', configManager.getPrefix()) + " disabilitato.");
    }

    // ---------------- Command /banana ----------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Questa riga converte i codici & in colori reali
        String prefix = ChatColor.translateAlternateColorCodes('&', configManager.getPrefix());

        if (!(sender instanceof Player)) {
            sender.sendMessage(prefix + ChatColor.RED + " Comando eseguibile solo da giocatori.");
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("bananaac.admin")) {
            p.sendMessage(prefix + ChatColor.RED + " Non hai permessi.");
            return true;
        }
        if (args.length == 0) {
            p.sendMessage(prefix + ChatColor.GREEN + " BananaAntiCheat attivo.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            configManager.load();

            // Aggiorniamo il prefix colorato dopo il reload
            String newPrefix = ChatColor.translateAlternateColorCodes('&', configManager.getPrefix());
            p.sendMessage(newPrefix + ChatColor.GREEN + " Plugin e Configurazione ricaricati con successo.");
            return true;
        }
        p.sendMessage(prefix + ChatColor.RED + " Comando non riconosciuto.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }

    // ---------------- Bukkit event checks ----------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        dataMap.put(e.getPlayer().getUniqueId(), new PlayerData(e.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        dataMap.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        if (p.hasPermission("bananaac.bypass")) return;

        PlayerData st = getData(p);

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        // SPEED - Reso molto meno aggressivo
        if (configManager.isSpeedEnabled() && !p.isFlying() && !p.isInsideVehicle()) {
            double maxSpeed = configManager.getSpeedMaxSpeed();
            // Consideriamo solo movimenti significativi e aggiungiamo tolleranza
            if (horizontal > maxSpeed && p.getFallDistance() < 0.1 && p.isOnGround()) {
                st.incrementSpeedViolations();
                // Flagga solo dopo multiple violazioni consecutive
                if (st.getSpeedViolations() > 3) {
                    flag(p, "Speed");
                    st.resetSpeedViolations();
                }
            } else {
                st.decrementSpeedViolations();
            }
        }

        // STEP - Migliorato per evitare false positive con scale/lastre
        if (configManager.isStepEnabled() && !p.isFlying() && !p.isInsideVehicle()) {
            double yDiff = to.getY() - from.getY();
            // Solo se è un vero step (non scale, non lastre)
            if (yDiff > configManager.getStepMaxHeight() && yDiff < 10.0 && p.isOnGround()) {
                // Verifica che non ci siano scale o lastre nelle vicinanze
                if (!hasStairsOrSlabsNearby(to) && !hasStairsOrSlabsNearby(from)) {
                    flag(p, "Step");
                }
            }
        }

        // JESUS - Migliorato per considerare barche e situazioni legittime
        if (configManager.isJesusEnabled() && !p.isFlying() && !p.isInsideVehicle()) {
            Block under = to.getBlock().getRelative(0, -1, 0);
            if ((under.getType() == Material.WATER || under.getType() == Material.STATIONARY_WATER)) {
                // Solo se sta davvero camminando sull'acqua (non nuotando)
                if (p.getVelocity().getY() >= -0.1 && p.getLocation().getY() > under.getY() + 0.9) {
                    st.incrementJesusViolations();
                    if (st.getJesusViolations() > 5) {
                        flag(p, "Jesus");
                        st.resetJesusViolations();
                    }
                } else {
                    st.decrementJesusViolations();
                }
            } else {
                st.resetJesusViolations();
            }
        }

        // SPIDER - Molto più preciso
        if (configManager.isSpiderEnabled() && !p.isFlying() && !p.isInsideVehicle()) {
            if (isAgainstWall(p) && to.getY() > from.getY() + 0.1 && !p.isOnGround() && p.getFallDistance() < 0.5) {
                // Verifica che non ci siano ladder/vine nelle vicinanze
                if (!hasClimbableNearby(p.getLocation())) {
                    st.incrementSpiderViolations();
                    if (st.getSpiderViolations() > 4) {
                        flag(p, "Spider");
                        st.resetSpiderViolations();
                    }
                } else {
                    st.resetSpiderViolations();
                }
            } else {
                st.decrementSpiderViolations();
            }
        }

        // NOSLOW - Più realistico
        if (configManager.isNoslowEnabled() && p.isBlocking() && p.isSneaking()) {
            if (horizontal > configManager.getNoslowMoveThreshold()) {
                st.incrementNoslowViolations();
                if (st.getNoslowViolations() > 3) {
                    flag(p, "NoSlow");
                    st.resetNoslowViolations();
                }
            } else {
                st.decrementNoslowViolations();
            }
        }

        st.setLastMove(System.currentTimeMillis());
        st.setLastLocation(to);
    }

    // Metodi helper migliorati
    private boolean hasStairsOrSlabsNearby(Location loc) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = loc.getBlock().getRelative(x, y, z);
                    Material type = block.getType();
                    if (type.toString().contains("STAIRS") || type.toString().contains("SLAB") ||
                            type.toString().contains("STEP") || type == Material.SOUL_SAND) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasClimbableNearby(Location loc) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = loc.getBlock().getRelative(x, y, z);
                    Material type = block.getType();
                    if (type == Material.LADDER || type == Material.VINE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isAgainstWall(Player player) {
        Location loc = player.getLocation();
        int solidBlocks = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                Block block = loc.getBlock().getRelative(x, 0, z);
                if (block.getType().isSolid()) {
                    solidBlocks++;
                }
            }
        }
        return solidBlocks >= 3; // Deve essere davvero contro un muro
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        if (p.hasPermission("bananaac.bypass")) return;

        PlayerData st = getData(p);
        long now = System.currentTimeMillis();

        // FASTPLACE - Ottimizzato per Telly Bridge (Shift+W+Click destro rapido)
        if (configManager.isFastplaceEnabled()) {
            long diff = now - st.getLastPlace();
            if (st.getLastPlace() > 0 && diff < configManager.getFastplaceVlMs()) {
                st.incrementFastplaceViolations();
                // Soglia molto più alta per permettere Telly Bridge legittimo
                if (st.getFastplaceViolations() > 12) { // Era 8, ora 12 per Telly Bridge
                    flag(p, "FastPlace");
                    st.resetFastplaceViolations();
                }
            } else {
                st.decrementFastplaceViolations();
            }
        }

        // SCAFFOLD - Molto più accurato e tollerante per building veloce
        if (configManager.isScaffoldEnabled()) {
            Block placedBlock = e.getBlockPlaced();
            Location playerLoc = p.getLocation();
            double heightDiff = playerLoc.getY() - placedBlock.getY();

            // Solo se piazza sotto di sé mentre è in aria E sta cadendo
            if (heightDiff > 2.0 && heightDiff < 5.0 && !p.isOnGround() && p.getFallDistance() > 2.0) {
                // Verifica che il blocco sia effettivamente sotto di lui (non di lato)
                double horizontalDist = Math.sqrt(
                        Math.pow(playerLoc.getX() - placedBlock.getX() - 0.5, 2) +
                                Math.pow(playerLoc.getZ() - placedBlock.getZ() - 0.5, 2)
                );

                if (horizontalDist < 1.0) { // Molto vicino sotto di lui
                    st.incrementScaffoldViolations();
                    if (st.getScaffoldViolations() > 4) { // Serve conferma multipla
                        flag(p, "Scaffold");
                        st.resetScaffoldViolations();
                    }
                } else {
                    st.decrementScaffoldViolations();
                }
            } else {
                st.decrementScaffoldViolations();
            }
        }
        st.setLastPlace(now);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        if (p.hasPermission("bananaac.bypass")) return;

        PlayerData st = getData(p);
        long now = System.currentTimeMillis();

        // FASTBREAK - Meno aggressivo e considera hardness dei blocchi
        if (configManager.isFastbreakEnabled()) {
            long diff = now - st.getLastBreak();

            // Ignora blocchi che si rompono velocemente (slime, leaves, wool, etc)
            Material blockType = e.getBlock().getType();
            boolean isSoftBlock = blockType == Material.SLIME_BLOCK ||
                    blockType.toString().contains("LEAVES") ||
                    blockType.toString().contains("WOOL") ||
                    blockType == Material.TNT ||
                    blockType == Material.SPONGE ||
                    blockType.toString().contains("CARPET");

            if (!isSoftBlock && diff > 0 && diff < configManager.getFastbreakVlMs()) {
                st.incrementFastbreakViolations();
                if (st.getFastbreakViolations() > 8) { // Era 5, ora 8
                    flag(p, "FastBreak");
                    st.resetFastbreakViolations();
                }
            } else {
                st.decrementFastbreakViolations();
            }
        }

        // NUKER - Soglia più alta
        if (configManager.isNukerEnabled()) {
            st.tickBreak();
            if (st.getBreaksPerSecond() > configManager.getNukerBreaksPerSec()) {
                flag(p, "Nuker");
                st.resetBreaks();
            }
        }
        st.setLastBreak(now);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        // InventoryWalk check rimosso - troppo buggy
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        // InventoryWalk check rimosso - troppo buggy
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (p.hasPermission("bananaac.bypass")) return;

        if(configManager.isAutoarmorEnabled()) {
            PlayerData st = getData(p);
            long now = System.currentTimeMillis();
            if (st.getLastInventoryAction() > 0 && now - st.getLastInventoryAction() < configManager.getAutoarmorVlMs()) {
                st.incrementAutoarmorViolations();
                if (st.getAutoarmorViolations() > 8) { // Più tollerante
                    flag(p, "AutoArmor");
                    st.resetAutoarmorViolations();
                }
            } else {
                st.decrementAutoarmorViolations();
            }
            st.setLastInventoryAction(now);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (p.hasPermission("bananaac.bypass")) return;

        if (configManager.isNofallEnabled() && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            // Solo controlla se il danno è completamente assente con alta caduta
            if (p.getFallDistance() > configManager.getNofallMinFallDist() && e.getDamage() < 0.5) {
                PlayerData st = getData(p);
                st.incrementNofallViolations();
                if (st.getNofallViolations() > 3) {
                    flag(p, "NoFall");
                    st.resetNofallViolations();
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        Player attacker = (Player) e.getDamager();
        if (attacker.hasPermission("bananaac.bypass")) return;

        PlayerData st = getData(attacker);

        // CRITICALS - Meno sensibile
        if (configManager.isCriticalsEnabled()) {
            if (attacker.getFallDistance() > 0.0 && !attacker.isOnGround() && attacker.getVelocity().getY() < 0) {
                st.incrementCriticals();
                if (st.getCriticalsInWindow() > configManager.getCriticalsMaxPerSec()) {
                    flag(attacker, "Criticals");
                    st.resetCriticals();
                }
            }
        }

        // VELOCITY - Più realistico
        if (configManager.isVelocityEnabled() && e.getEntity() instanceof Player) {
            final Player victim = (Player) e.getEntity();
            final Vector initialVelocity = victim.getVelocity();
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Vector newVelocity = victim.getVelocity();
                double velocityReduction = (initialVelocity.length() - newVelocity.length()) / initialVelocity.length();
                if (velocityReduction > 0.8 && newVelocity.getY() < configManager.getVelocityMinKnockback()) {
                    PlayerData victimData = getData(victim);
                    victimData.incrementVelocityViolations();
                    if (victimData.getVelocityViolations() > 4) {
                        flag(victim, "Velocity");
                        victimData.resetVelocityViolations();
                    }
                }
            }, 2L);
        }
    }

    // ---------------- ProtocolLib packet listener ----------------
    private class ProtocolListener extends PacketAdapter {
        ProtocolListener(JavaPlugin plugin) {
            super(plugin,
                  PacketType.Play.Client.FLYING, PacketType.Play.Client.POSITION,
                  PacketType.Play.Client.POSITION_LOOK, PacketType.Play.Client.USE_ENTITY,
                  PacketType.Play.Client.ARM_ANIMATION, PacketType.Play.Client.KEEP_ALIVE
            );
        }

        @Override
        public void onPacketReceiving(PacketEvent event) {
            Player p = event.getPlayer();
            if (p == null || p.getGameMode() != GameMode.SURVIVAL || p.hasPermission("bananaac.bypass")) return;

            PacketType type = event.getPacketType();
            PacketContainer packet = event.getPacket();
            PlayerData st = getData(p);

            if (type == PacketType.Play.Client.FLYING || type == PacketType.Play.Client.POSITION || type == PacketType.Play.Client.POSITION_LOOK) {
                // TIMER - Meno aggressivo e ignora giocatori fermi/freezati
                if (configManager.isTimerEnabled()) {
                    long now = System.currentTimeMillis();
                    long last = st.getLastFlyingPacket();
                    if (last > 0) {
                        long interval = now - last;

                        // Verifica se il player si sta muovendo effettivamente
                        Location currentLoc = p.getLocation();
                        Location lastLoc = st.getLastLocation();
                        boolean isMoving = false;

                        if (lastLoc != null && lastLoc.getWorld().equals(currentLoc.getWorld())) {
                            double distance = lastLoc.distance(currentLoc);
                            isMoving = distance > 0.01; // Movimento minimo rilevabile
                        }

                        // Flagga solo se si sta muovendo E l'intervallo è troppo basso
                        if (interval < configManager.getTimerMinMs() && isMoving) {
                            st.incrementTimerViolations();
                            if (st.getTimerViolations() > 15) { // Ancora più tollerante (era 10)
                                flag(p, "Timer");
                                st.resetTimerViolations();
                            }
                        } else {
                            st.decrementTimerViolations();
                        }
                    }
                    st.setLastFlyingPacket(now);
                }

                // FLY - Molto migliorato
                if (configManager.isFlyEnabled() && !p.getAllowFlight() && p.getVehicle() == null) {
                    if (p.getFallDistance() == 0.0 && !p.isOnGround() && !hasBlocksNearby(p.getLocation())) {
                        st.incrementAirborneTicks();
                        if (st.getTicksAirborne() > 20) { // Più tollerante
                            flag(p, "Fly");
                            st.resetAirborneTicks();
                        }
                    } else {
                        st.resetAirborneTicks();
                    }
                }

                // INVENTORY WALK - RIMOSSO (troppo buggy con GUI normali)
            }

            if (type == PacketType.Play.Client.ARM_ANIMATION) {
                // AUTOCLICKER - Separato dal mining per evitare falsi positivi
                if (configManager.isAutoclickerEnabled()) {
                    long now = System.currentTimeMillis();
                    long timeSinceLastBreak = now - st.getLastBreak();

                    // Ignora click se sta minando (ultimo break < 500ms fa)
                    if (timeSinceLastBreak > 500) {
                        st.tickClick();
                        if (st.getCps() > configManager.getAutoclickerMaxCPS()) {
                            st.incrementAutoclickerViolations();
                            if (st.getAutoclickerViolations() > 8) { // Più tollerante
                                flag(p, "AutoClicker");
                                st.resetAutoclickerViolations();
                            }
                        } else {
                            st.decrementAutoclickerViolations();
                        }
                    }
                }

                // FASTBOW - Migliorato
                if (configManager.isFastbowEnabled() && p.getItemInHand() != null &&
                        p.getItemInHand().getType() == Material.BOW) {
                    long now = System.currentTimeMillis();
                    if (st.getLastBow() > 0 && now - st.getLastBow() < configManager.getFastbowVlMs()) {
                        st.incrementFastbowViolations();
                        if (st.getFastbowViolations() > 3) {
                            flag(p, "FastBow");
                            st.resetFastbowViolations();
                        }
                    } else {
                        st.decrementFastbowViolations();
                    }
                    st.setLastBow(now);
                }
            }

            if (type == PacketType.Play.Client.USE_ENTITY) {
                try {
                    Entity target = protocolManager.getEntityFromID(p.getWorld(), packet.getIntegers().read(0));
                    if(target == null) return;

                    // REACH - Più preciso
                    if (configManager.isReachEnabled()) {
                        double dist = p.getEyeLocation().distance(target.getLocation()) - 0.6; // Più tollerante
                        if (dist > configManager.getReachMaxReach()) {
                            st.incrementReachViolations();
                            if (st.getReachViolations() > 3) {
                                flag(p, "Reach");
                                st.resetReachViolations();
                            }
                        } else {
                            st.decrementReachViolations();
                        }
                    }

                    // KILLAURA - Molto migliorato
                    if (configManager.isKillauraEnabled()) {
                        st.trackAttack(target.getUniqueId());
                        int distinctTargets = st.getDistinctRecentAttacks();
                        if (distinctTargets > configManager.getKillauraMultiEntityThreshold()) {
                            st.incrementKillauraViolations();
                            if (st.getKillauraViolations() > 2) {
                                flag(p, "KillAura");
                                st.resetKillauraViolations();
                            }
                        } else {
                            st.decrementKillauraViolations();
                        }
                    }

                    // AIMBOT - Più realistico
                    if (configManager.isAimbotEnabled()) {
                        Vector toTarget = target.getLocation().toVector().subtract(p.getEyeLocation().toVector()).normalize();
                        Vector playerDirection = p.getLocation().getDirection();
                        double dot = toTarget.dot(playerDirection);
                        if (dot > configManager.getAimbotDotThreshold()) {
                            st.incrementAimbotViolations();
                            if (st.getAimbotViolations() > 8) { // Molto tollerante
                                flag(p, "Aimbot");
                                st.resetAimbotViolations();
                            }
                        } else {
                            st.decrementAimbotViolations();
                        }
                    }
                } catch (Exception ignored) {}
            }

            if(type == PacketType.Play.Client.KEEP_ALIVE) {
                if (configManager.isBadpacketsEnabled()) {
                    long now = System.currentTimeMillis();
                    if(st.getLastKeepAlive() > 0 && now - st.getLastKeepAlive() < configManager.getBadpacketsMinIntervalMs()){
                        st.incrementBadpacketsViolations();
                        if (st.getBadpacketsViolations() > 10) { // Molto tollerante
                            flag(p, "BadPackets");
                            st.resetBadpacketsViolations();
                        }
                    } else {
                        st.decrementBadpacketsViolations();
                    }
                    st.setLastKeepAlive(now);
                }
            }
        }
    }

    // Helper per il check Fly
    private boolean hasBlocksNearby(Location loc) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -3; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (loc.getBlock().getRelative(x, y, z).getType().isSolid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---------------- Helper methods ----------------

    private void flag(Player p, String checkName) {
        if (p == null || p.hasPermission("bananaac.bypass")) return;

        PlayerData st = getData(p);
        int vlToAdd = configManager.getCheckVl(checkName);
        st.addVl(checkName, vlToAdd);

        int currentVl = st.getVl(checkName);
        int threshold = configManager.getCheckVlThreshold(checkName);

        // Traduci TUTTO prima, incluso il prefix originale
        String translatedPrefix = ChatColor.translateAlternateColorCodes('&', configManager.getPrefix());
        String translatedFlagMsg = ChatColor.translateAlternateColorCodes('&', configManager.getFlagMessage());

        String finalMsg = translatedFlagMsg
                .replace("%prefix%", translatedPrefix)
                .replace("%player%", p.getName())
                .replace("%check%", checkName)
                .replace("%vl%", String.valueOf(currentVl));

        Bukkit.getOnlinePlayers().stream()
                .filter(staff -> staff.hasPermission("bananaac.alerts"))
                .forEach(staff -> {
                    staff.sendMessage(finalMsg);
                    staff.playSound(staff.getLocation(), Sound.NOTE_PLING, 1f, 1.5f);
                });

        if (configManager.isLocalLogsEnabled()) {
            getLogger().info("[FLAG] " + p.getName() + " failed " + checkName + " (VL: " + currentVl + ")");
        }

        if (currentVl >= threshold) {
            applyPunishment(p, checkName, currentVl);
        }
    }

    private void applyPunishment(Player p, String checkName, int vl) {
        String banMsg = ChatColor.translateAlternateColorCodes('&', configManager.getBanMessage());
        Bukkit.getScheduler().runTask(this, () -> {
            p.kickPlayer(banMsg);
            getLogger().info("[KICK] " + p.getName() + " è stato kickato per Cheating ("+ checkName +", VL: "+ vl +").");
            String banCommand = "kick " + p.getName() + " Cheating";
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), banCommand);
        });
    }

    private PlayerData getData(Player p) {
        return dataMap.computeIfAbsent(p.getUniqueId(), PlayerData::new);
    }

    // ---------------- PlayerData inner class ----------------
    private static class PlayerData {
        private final UUID uuid;
        private final Map<String, Integer> vlMap = new HashMap<>();

        private Location lastLocation = null;
        private long lastFlyingPacket = 0L, lastPlace = 0L, lastBreak = 0L;
        private long lastKeepAlive = 0L, lastBow = 0L, lastInventoryAction = 0L, lastMoveMs = 0L;
        private int ticksAirborne = 0;

        private final Deque<Long> clicks = new ArrayDeque<>();
        private final Deque<Long> breaks = new ArrayDeque<>();
        private final Deque<UUID> recentAttacks = new ArrayDeque<>();
        private final Deque<Long> criticals = new ArrayDeque<>();

        // Contatori per le violazioni consecutive - evita falsi positivi
        private int speedViolations = 0;
        private int jesusViolations = 0;
        private int spiderViolations = 0;
        private int noslowViolations = 0;
        private int fastplaceViolations = 0;
        private int fastbreakViolations = 0;
        private int nofallViolations = 0;
        private int autoclickerViolations = 0;
        private int autoarmorViolations = 0;
        private int timerViolations = 0;
        private int blinkViolations = 0;
        private int reachViolations = 0;
        private int killauraViolations = 0;
        private int aimbotViolations = 0;
        private int badpacketsViolations = 0;
        private int fastbowViolations = 0;
        private int velocityViolations = 0;
        private int scaffoldViolations = 0;

        PlayerData(UUID id) { this.uuid = id; }

        // Getters e Setters base
        void setLastLocation(Location l) { this.lastLocation = l; }
        Location getLastLocation() { return lastLocation; }
        void setLastFlyingPacket(long t) { this.lastFlyingPacket = t; }
        long getLastFlyingPacket() { return lastFlyingPacket; }
        void setLastPlace(long t) { this.lastPlace = t; }
        long getLastPlace() { return lastPlace; }
        void setLastBreak(long t) { this.lastBreak = t; }
        long getLastBreak() { return lastBreak; }
        void setLastKeepAlive(long t) { this.lastKeepAlive = t; }
        long getLastKeepAlive() { return lastKeepAlive; }
        void setLastBow(long t) { this.lastBow = t; }
        long getLastBow() { return lastBow; }
        void setLastInventoryAction(long t) { this.lastInventoryAction = t; }
        long getLastInventoryAction() { return lastInventoryAction; }
        void setLastMove(long t) { this.lastMoveMs = t; }

        void incrementAirborneTicks() { this.ticksAirborne++; }
        void resetAirborneTicks() { this.ticksAirborne = 0; }
        int getTicksAirborne() { return ticksAirborne; }

        // Metodi per gestire le violazioni consecutive
        void incrementSpeedViolations() { speedViolations = Math.min(speedViolations + 1, 10); }
        void decrementSpeedViolations() { speedViolations = Math.max(speedViolations - 1, 0); }
        void resetSpeedViolations() { speedViolations = 0; }
        int getSpeedViolations() { return speedViolations; }

        void incrementJesusViolations() { jesusViolations = Math.min(jesusViolations + 1, 10); }
        void decrementJesusViolations() { jesusViolations = Math.max(jesusViolations - 1, 0); }
        void resetJesusViolations() { jesusViolations = 0; }
        int getJesusViolations() { return jesusViolations; }

        void incrementSpiderViolations() { spiderViolations = Math.min(spiderViolations + 1, 10); }
        void decrementSpiderViolations() { spiderViolations = Math.max(spiderViolations - 1, 0); }
        void resetSpiderViolations() { spiderViolations = 0; }
        int getSpiderViolations() { return spiderViolations; }

        void incrementNoslowViolations() { noslowViolations = Math.min(noslowViolations + 1, 10); }
        void decrementNoslowViolations() { noslowViolations = Math.max(noslowViolations - 1, 0); }
        void resetNoslowViolations() { noslowViolations = 0; }
        int getNoslowViolations() { return noslowViolations; }

        void incrementFastplaceViolations() { fastplaceViolations = Math.min(fastplaceViolations + 1, 15); }
        void decrementFastplaceViolations() { fastplaceViolations = Math.max(fastplaceViolations - 1, 0); }
        void resetFastplaceViolations() { fastplaceViolations = 0; }
        int getFastplaceViolations() { return fastplaceViolations; }

        void incrementFastbreakViolations() { fastbreakViolations = Math.min(fastbreakViolations + 1, 10); }
        void decrementFastbreakViolations() { fastbreakViolations = Math.max(fastbreakViolations - 1, 0); }
        void resetFastbreakViolations() { fastbreakViolations = 0; }
        int getFastbreakViolations() { return fastbreakViolations; }

        void incrementNofallViolations() { nofallViolations = Math.min(nofallViolations + 1, 10); }
        void resetNofallViolations() { nofallViolations = 0; }
        int getNofallViolations() { return nofallViolations; }

        void incrementAutoclickerViolations() { autoclickerViolations = Math.min(autoclickerViolations + 1, 10); }
        void decrementAutoclickerViolations() { autoclickerViolations = Math.max(autoclickerViolations - 1, 0); }
        void resetAutoclickerViolations() { autoclickerViolations = 0; }
        int getAutoclickerViolations() { return autoclickerViolations; }

        void incrementAutoarmorViolations() { autoarmorViolations = Math.min(autoarmorViolations + 1, 15); }
        void decrementAutoarmorViolations() { autoarmorViolations = Math.max(autoarmorViolations - 1, 0); }
        void resetAutoarmorViolations() { autoarmorViolations = 0; }
        int getAutoarmorViolations() { return autoarmorViolations; }

        void incrementTimerViolations() { timerViolations = Math.min(timerViolations + 1, 20); }
        void decrementTimerViolations() { timerViolations = Math.max(timerViolations - 1, 0); }
        void resetTimerViolations() { timerViolations = 0; }
        int getTimerViolations() { return timerViolations; }

        void incrementBlinkViolations() { blinkViolations = Math.min(blinkViolations + 1, 10); }
        void decrementBlinkViolations() { blinkViolations = Math.max(blinkViolations - 1, 0); }
        void resetBlinkViolations() { blinkViolations = 0; }
        int getBlinkViolations() { return blinkViolations; }

        void incrementReachViolations() { reachViolations = Math.min(reachViolations + 1, 10); }
        void decrementReachViolations() { reachViolations = Math.max(reachViolations - 1, 0); }
        void resetReachViolations() { reachViolations = 0; }
        int getReachViolations() { return reachViolations; }

        void incrementKillauraViolations() { killauraViolations = Math.min(killauraViolations + 1, 10); }
        void decrementKillauraViolations() { killauraViolations = Math.max(killauraViolations - 1, 0); }
        void resetKillauraViolations() { killauraViolations = 0; }
        int getKillauraViolations() { return killauraViolations; }

        void incrementAimbotViolations() { aimbotViolations = Math.min(aimbotViolations + 1, 15); }
        void decrementAimbotViolations() { aimbotViolations = Math.max(aimbotViolations - 1, 0); }
        void resetAimbotViolations() { aimbotViolations = 0; }
        int getAimbotViolations() { return aimbotViolations; }

        void incrementBadpacketsViolations() { badpacketsViolations = Math.min(badpacketsViolations + 1, 20); }
        void decrementBadpacketsViolations() { badpacketsViolations = Math.max(badpacketsViolations - 1, 0); }
        void resetBadpacketsViolations() { badpacketsViolations = 0; }
        int getBadpacketsViolations() { return badpacketsViolations; }

        void incrementFastbowViolations() { fastbowViolations = Math.min(fastbowViolations + 1, 10); }
        void decrementFastbowViolations() { fastbowViolations = Math.max(fastbowViolations - 1, 0); }
        void resetFastbowViolations() { fastbowViolations = 0; }
        int getFastbowViolations() { return fastbowViolations; }

        void incrementVelocityViolations() { velocityViolations = Math.min(velocityViolations + 1, 10); }
        void resetVelocityViolations() { velocityViolations = 0; }
        int getVelocityViolations() { return velocityViolations; }

        void incrementScaffoldViolations() { scaffoldViolations = Math.min(scaffoldViolations + 1, 10); }
        void decrementScaffoldViolations() { scaffoldViolations = Math.max(scaffoldViolations - 1, 0); }
        void resetScaffoldViolations() { scaffoldViolations = 0; }
        int getScaffoldViolations() { return scaffoldViolations; }

        // Metodi per CPS tracking
        void tickClick() {
            long now = System.currentTimeMillis();
            clicks.addLast(now);
            while (!clicks.isEmpty() && now - clicks.getFirst() > 1000) clicks.removeFirst();
        }
        int getCps() { return clicks.size(); }

        // Metodi per break tracking
        void tickBreak() {
            long now = System.currentTimeMillis();
            breaks.addLast(now);
            while (!breaks.isEmpty() && now - breaks.getFirst() > 1000) breaks.removeFirst();
        }
        int getBreaksPerSecond() { return breaks.size(); }
        void resetBreaks() { breaks.clear(); }

        // Metodi per attack tracking (KillAura)
        void trackAttack(UUID id) {
            recentAttacks.addLast(id);
            long now = System.currentTimeMillis();
            // Rimuovi attacchi più vecchi di 3 secondi
            while (recentAttacks.size() > 15) recentAttacks.removeFirst();
        }
        int getDistinctRecentAttacks() { return new HashSet<>(recentAttacks).size(); }

        // Metodi per criticals tracking
        void incrementCriticals() {
            long now = System.currentTimeMillis();
            criticals.addLast(now);
            while (!criticals.isEmpty() && now - criticals.getFirst() > 1000) criticals.removeFirst();
        }
        int getCriticalsInWindow() { return criticals.size(); }
        void resetCriticals() { criticals.clear(); }

        // VL Management
        void addVl(String check, int inc) { vlMap.put(check, vlMap.getOrDefault(check, 0) + inc); }
        int getVl(String check) { return vlMap.getOrDefault(check, 0); }
    }
}

/**
 * Classe dedicata a caricare e gestire tutte le impostazioni dal file config.yml
 */
class ConfigManager {
    private final BananaAntiCheat plugin;
    private FileConfiguration cfg;

    // Global settings
    private String prefix;
    private String flagMessage;
    private String banMessage;
    private int maxVl;
    private boolean dbEnabled;
    private String dbType;
    private String dbHost;
    private int dbPort;
    private String dbDatabase;
    private String dbUsername;
    private String dbPassword;
    private boolean localLogs;

    // Check settings
    private boolean speedEnabled; private double speedMaxSpeed;
    private boolean flyEnabled;
    private boolean nofallEnabled; private double nofallMinFallDist;
    private boolean jesusEnabled;
    private boolean spiderEnabled;
    private boolean stepEnabled; private double stepMaxHeight;
    private boolean killauraEnabled; private int killauraMultiEntityThreshold;
    private boolean reachEnabled; private double reachMaxReach;
    private boolean aimbotEnabled; private double aimbotDotThreshold;
    private boolean velocityEnabled; private double velocityMinKnockback;
    private boolean criticalsEnabled; private int criticalsMaxPerSec;
    private boolean autoclickerEnabled; private int autoclickerMaxCPS;
    private boolean scaffoldEnabled;
    private boolean nukerEnabled; private int nukerBreaksPerSec;
    private boolean fastplaceEnabled; private int fastplaceVlMs;
    private boolean noslowEnabled; private double noslowMoveThreshold;
    private boolean fastbreakEnabled; private int fastbreakVlMs;
    private boolean badpacketsEnabled; private int badpacketsMinIntervalMs;
    private boolean fastbowEnabled; private int fastbowVlMs;
    private boolean autoarmorEnabled; private int autoarmorVlMs;
    private boolean timerEnabled; private int timerMinMs;

    public ConfigManager(BananaAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();

        // Global
        prefix = cfg.getString("prefix", "&c&l[&eBananaAntiCheat&c&l] &7» &r");
        flagMessage = cfg.getString("flag-message", "%prefix% &c%player% &7ha fallito il check &e%check% &7(&b%vl%&7)");
        maxVl = cfg.getInt("max-vl", 50);
        banMessage = cfg.getString("ban-message", "&c&l[&e&lBananaAntiCheat&c&l] &cCheating (AntiCheat)");
        localLogs = cfg.getBoolean("local-logs", true);

        // Database
        dbEnabled = cfg.getBoolean("database.enabled", false);
        dbType = cfg.getString("database.type", "mysql");
        dbHost = cfg.getString("database.host", "localhost");
        dbPort = cfg.getInt("database.port", 3306);
        dbDatabase = cfg.getString("database.database", "banana");
        dbUsername = cfg.getString("database.username", "root");
        dbPassword = cfg.getString("database.password", "password");

        // Checks con valori di fallback ottimizzati
        speedEnabled = cfg.getBoolean("check-settings.speed.enabled", true);
        speedMaxSpeed = cfg.getDouble("check-settings.speed.maxSpeed", 0.5);

        flyEnabled = cfg.getBoolean("check-settings.fly.enabled", true);

        nofallEnabled = cfg.getBoolean("check-settings.nofall.enabled", true);
        nofallMinFallDist = cfg.getDouble("check-settings.nofall.min-fall-dist", 3.5);

        jesusEnabled = cfg.getBoolean("check-settings.jesus.enabled", true);
        spiderEnabled = cfg.getBoolean("check-settings.spider.enabled", true);

        stepEnabled = cfg.getBoolean("check-settings.step.enabled", true);
        stepMaxHeight = cfg.getDouble("check-settings.step.max-step-height", 1.2);

        killauraEnabled = cfg.getBoolean("check-settings.killaura.enabled", true);
        killauraMultiEntityThreshold = cfg.getInt("check-settings.killaura.multi-entity-threshold", 6);

        reachEnabled = cfg.getBoolean("check-settings.reach.enabled", true);
        reachMaxReach = cfg.getDouble("check-settings.reach.maxReach", 4.2);

        aimbotEnabled = cfg.getBoolean("check-settings.aimbot.enabled", true);
        aimbotDotThreshold = cfg.getDouble("check-settings.aimbot.dot-threshold", 0.98);

        velocityEnabled = cfg.getBoolean("check-settings.velocity.enabled", true);
        velocityMinKnockback = cfg.getDouble("check-settings.velocity.min-knockback", -0.1);

        criticalsEnabled = cfg.getBoolean("check-settings.criticals.enabled", true);
        criticalsMaxPerSec = cfg.getInt("check-settings.criticals.max-critical-per-sec", 8);

        autoclickerEnabled = cfg.getBoolean("check-settings.autoclicker.enabled", true);
        autoclickerMaxCPS = cfg.getInt("check-settings.autoclicker.maxCPS", 37);

        scaffoldEnabled = cfg.getBoolean("check-settings.scaffold.enabled", true);

        nukerEnabled = cfg.getBoolean("check-settings.nuker.enabled", true);
        nukerBreaksPerSec = cfg.getInt("check-settings.nuker.breaks-per-sec", 15);

        fastplaceEnabled = cfg.getBoolean("check-settings.fastplace.enabled", true);
        fastplaceVlMs = cfg.getInt("check-settings.fastplace.vl-ms", 60);

        noslowEnabled = cfg.getBoolean("check-settings.noslow.enabled", true);
        noslowMoveThreshold = cfg.getDouble("check-settings.noslow.move-threshold", 0.15);

        fastbreakEnabled = cfg.getBoolean("check-settings.fastbreak.enabled", true);
        fastbreakVlMs = cfg.getInt("check-settings.fastbreak.vl-ms", 150);

        badpacketsEnabled = cfg.getBoolean("check-settings.badpackets.enabled", true);
        badpacketsMinIntervalMs = cfg.getInt("check-settings.badpackets.min-interval-ms", 20);

        fastbowEnabled = cfg.getBoolean("check-settings.fastbow.enabled", true);
        fastbowVlMs = cfg.getInt("check-settings.fastbow.vl-ms", 800);

        autoarmorEnabled = cfg.getBoolean("check-settings.autoarmor.enabled", true);
        autoarmorVlMs = cfg.getInt("check-settings.autoarmor.vl-ms", 120);

        timerEnabled = cfg.getBoolean("check-settings.timer.enabled", true);
        timerMinMs = cfg.getInt("check-settings.timer.min-ms", 35);
    }

    // Dynamic getters for per-check VL and Threshold
    public int getCheckVl(String checkName) {
        return cfg.getInt("check-settings." + checkName.toLowerCase() + ".vl", 1);
    }

    public int getCheckVlThreshold(String checkName) {
        return cfg.getInt("check-settings." + checkName.toLowerCase() + ".vl-threshold", this.maxVl);
    }

    // Getters for all settings
    public String getPrefix() { return prefix; }
    public String getFlagMessage() { return flagMessage; }
    public String getBanMessage() { return banMessage; }
    public boolean isLocalLogsEnabled() { return localLogs; }

    public boolean isSpeedEnabled() { return speedEnabled; }
    public double getSpeedMaxSpeed() { return speedMaxSpeed; }
    public boolean isFlyEnabled() { return flyEnabled; }
    public boolean isNofallEnabled() { return nofallEnabled; }
    public double getNofallMinFallDist() { return nofallMinFallDist; }
    public boolean isJesusEnabled() { return jesusEnabled; }
    public boolean isSpiderEnabled() { return spiderEnabled; }
    public boolean isStepEnabled() { return stepEnabled; }
    public double getStepMaxHeight() { return stepMaxHeight; }
    public boolean isKillauraEnabled() { return killauraEnabled; }
    public int getKillauraMultiEntityThreshold() { return killauraMultiEntityThreshold; }
    public boolean isReachEnabled() { return reachEnabled; }
    public double getReachMaxReach() { return reachMaxReach; }
    public boolean isAimbotEnabled() { return aimbotEnabled; }
    public double getAimbotDotThreshold() { return aimbotDotThreshold; }
    public boolean isVelocityEnabled() { return velocityEnabled; }
    public double getVelocityMinKnockback() { return velocityMinKnockback; }
    public boolean isCriticalsEnabled() { return criticalsEnabled; }
    public int getCriticalsMaxPerSec() { return criticalsMaxPerSec; }
    public boolean isAutoclickerEnabled() { return autoclickerEnabled; }
    public int getAutoclickerMaxCPS() { return autoclickerMaxCPS; }
    public boolean isScaffoldEnabled() { return scaffoldEnabled; }
    public boolean isNukerEnabled() { return nukerEnabled; }
    public int getNukerBreaksPerSec() { return nukerBreaksPerSec; }
    public boolean isFastplaceEnabled() { return fastplaceEnabled; }
    public int getFastplaceVlMs() { return fastplaceVlMs; }
    public boolean isNoslowEnabled() { return noslowEnabled; }
    public double getNoslowMoveThreshold() { return noslowMoveThreshold; }
    public boolean isFastbreakEnabled() { return fastbreakEnabled; }
    public int getFastbreakVlMs() { return fastbreakVlMs; }
    public boolean isBadpacketsEnabled() { return badpacketsEnabled; }
    public int getBadpacketsMinIntervalMs() { return badpacketsMinIntervalMs; }
    public boolean isFastbowEnabled() { return fastbowEnabled; }
    public int getFastbowVlMs() { return fastbowVlMs; }
    public boolean isAutoarmorEnabled() { return autoarmorEnabled; }
    public int getAutoarmorVlMs() { return autoarmorVlMs; }
    public boolean isTimerEnabled() { return timerEnabled; }
    public int getTimerMinMs() { return timerMinMs; }
}