/*
 *  Village Defense - Protect villagers from hordes of zombies
 *  Copyright (c) 2026 Plugily Projects - maintained by Tigerpanzer_02 and contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package plugily.projects.villagedefense.creatures.v1_9_UP;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import plugily.projects.villagedefense.Main;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * @author Plajer
 * <p>
 * Created at 04.08.2023
 */
public class RideableCreatureEvents {

  private Main plugin;

  public RideableCreatureEvents(Main plugin) {
    this.plugin = plugin;
    ProtocolManager manager = ProtocolLibrary.getProtocolManager();
    manager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.STEER_VEHICLE) {
      @Override
      public void onPacketReceiving(PacketEvent event) {
        handlePreSteer(event);
      }
    });
  }

  private void handlePreSteer(PacketEvent event) {
    Entity vehicle = event.getPlayer().getVehicle();
    if(vehicle == null) {
      return;
    }
    CustomRideableCreature.RideableType type = null;
    for(CustomRideableCreature.RideableType rideableType : CustomRideableCreature.RideableType.values()) {
      if(rideableType.name().equals(vehicle.getType().name().toUpperCase())) {
        type = rideableType;
        break;
      }
    }
    if(type == null) {
      return;
    }
    Optional<CustomRideableCreature> customRideableCreatureOptional = plugin.getEnemySpawnerRegistry().getRideableCreatureByName(type);
    if(!customRideableCreatureOptional.isPresent()) {
      return;
    }
    handleSteer(event, vehicle);
  }

  private void handleSteer(PacketEvent event, Entity vehicle) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    Optional<SteerInput> steerInputOptional = readSteerInput(packet);
    if(!steerInputOptional.isPresent()) {
      return;
    }
    SteerInput steerInput = steerInputOptional.get();
    if(steerInput.unmount) {
      return;
    }
    float sideways = steerInput.sideways;
    float forward = steerInput.forward;
    Location location = player.getLocation();
    double radians = Math.toRadians(location.getYaw());
    double x = -forward * Math.sin(radians) + sideways * Math.cos(radians);
    double z = forward * Math.cos(radians) + sideways * Math.sin(radians);
    Vector velocity = new Vector(x, 0.0, z).normalize().multiply(0.5);
    velocity.setY(vehicle.getVelocity().getY());
    if(!Double.isFinite(velocity.getX())) {
      velocity.setX(0);
    }
    if(!Double.isFinite(velocity.getZ())) {
      velocity.setZ(0);
    }
    if(steerInput.jump && vehicle.isOnGround()) {
      velocity.setY(0.5);
    }
    try {
      velocity.checkFinite();
      vehicle.setVelocity(velocity);
    } catch(Exception ignored) {
    }
  }

  private Optional<SteerInput> readSteerInput(PacketContainer packet) {
    // Older protocol versions expose sideways/forward as floats and jump/unmount as booleans.
    StructureModifier<Float> floats = packet.getFloat();
    StructureModifier<Boolean> booleans = packet.getBooleans();
    if(floats.size() >= 2 && booleans.size() >= 2) {
      return Optional.of(new SteerInput(
          floats.read(0),
          floats.read(1),
          booleans.read(0),
          booleans.read(1)
      ));
    }

    // Newer protocol versions wrap movement flags in a single Input object.
    Object input = packet.getModifier().readSafely(0);
    if(input == null) {
      return Optional.empty();
    }
    return readModernInput(input);
  }

  private Optional<SteerInput> readModernInput(Object input) {
    Boolean forward = readBoolean(input, "forward");
    Boolean backward = readBoolean(input, "backward");
    Boolean left = readBoolean(input, "left");
    Boolean right = readBoolean(input, "right");
    Boolean jump = readBoolean(input, "jump");
    Boolean shift = readBoolean(input, "shift");

    if(forward == null && backward == null && left == null && right == null && jump == null && shift == null) {
      return Optional.empty();
    }

    return Optional.of(new SteerInput(
        booleanValue(left) - booleanValue(right),
        booleanValue(forward) - booleanValue(backward),
        Boolean.TRUE.equals(jump),
        Boolean.TRUE.equals(shift)
    ));
  }

  private Boolean readBoolean(Object target, String name) {
    Boolean methodValue = readBooleanMethod(target, name);
    if(methodValue != null) {
      return methodValue;
    }
    return readBooleanField(target, name);
  }

  private Boolean readBooleanMethod(Object target, String name) {
    Class<?> type = target.getClass();
    while(type != null) {
      try {
        Method method = type.getDeclaredMethod(name);
        method.setAccessible(true);
        Object value = method.invoke(target);
        return value instanceof Boolean ? (Boolean) value : null;
      } catch(ReflectiveOperationException | RuntimeException ignored) {
        type = type.getSuperclass();
      }
    }
    return null;
  }

  private Boolean readBooleanField(Object target, String name) {
    Class<?> type = target.getClass();
    while(type != null) {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(target);
        return value instanceof Boolean ? (Boolean) value : null;
      } catch(ReflectiveOperationException | RuntimeException ignored) {
        type = type.getSuperclass();
      }
    }
    return null;
  }

  private int booleanValue(Boolean value) {
    return Boolean.TRUE.equals(value) ? 1 : 0;
  }

  private static class SteerInput {

    private final float sideways;
    private final float forward;
    private final boolean jump;
    private final boolean unmount;

    private SteerInput(float sideways, float forward, boolean jump, boolean unmount) {
      this.sideways = sideways;
      this.forward = forward;
      this.jump = jump;
      this.unmount = unmount;
    }
  }

}
