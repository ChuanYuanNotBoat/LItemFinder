package dev.litemfinder.navigation;

import java.util.ArrayList;
import java.util.List;

/** Removes only collinear forward vertices; no corner is shortcut through unobserved space. */
public final class PathGeometry {
    private PathGeometry() { }
    public static List<Waypoint> compress(List<Waypoint> points) {
        if(points.size()<3) return List.copyOf(points);
        List<Waypoint> result=new ArrayList<>(); result.add(points.getFirst());
        for(int index=1;index<points.size()-1;index++) {
            Waypoint a=result.getLast(),b=points.get(index),c=points.get(index+1);
            double ax=b.x()-a.x(),ay=b.y()-a.y(),az=b.z()-a.z();
            double bx=c.x()-b.x(),by=c.y()-b.y(),bz=c.z()-b.z();
            boolean sameSpace=a.scope().equals(b.scope())&&b.scope().equals(c.scope())
                    &&a.dimension().equals(b.dimension())&&b.dimension().equals(c.dimension());
            if(!sameSpace||Math.abs(ay*bz-az*by)>1E-9||Math.abs(az*bx-ax*bz)>1E-9
                    ||Math.abs(ax*by-ay*bx)>1E-9||ax*bx+ay*by+az*bz<=0) result.add(b);
        }
        result.add(points.getLast()); return List.copyOf(result);
    }
}
