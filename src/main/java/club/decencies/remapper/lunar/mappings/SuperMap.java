package club.decencies.remapper.lunar.mappings;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.RequiredArgsConstructor;
import org.objectweb.asm.Type;

// todo maybe only use this class for renaming everything...
// srg -> moj
// moj -> srg
// pro -> moj
// moj -> pro
// srg -> pro
// pro -> srg
public class SuperMap {

//    private final BiMap<String, String> proguardMojangClassMap = HashBiMap.create();
//    private final BiMap<String, String> proguardSeargeFieldMap = HashBiMap.create();
//    private final BiMap<String, String> proguardSeargeMethodMap = HashBiMap.create();
//    private final BiMap<String, String> seargeMojangFieldMap = HashBiMap.create();
//    private final BiMap<String, String> seargeMojangMethodMap = HashBiMap.create();
//
//    private final BiMap<String, String> lunarProguardClassMap = HashBiMap.create();
//    private final BiMap<String, String> lunarSeargeFieldMap = HashBiMap.create();
//    private final BiMap<String, String> lunarSeargeMethodMap = HashBiMap.create();



    @RequiredArgsConstructor
    public enum Provider implements LookupTable {
        PROGUARD {
            @Override
            protected Type lookupType0(Type type, Provider from, Provider to) {
                return null;
            }

            @Override
            protected Type lookupField0(Type owner, String name, Type type, Provider from, Provider to) {
                return null;
            }

            @Override
            protected Type lookupMethod0(Type owner, String name, Type type, Provider from, Provider to) {
                return null;
            }
        },
        SEARGE {
            @Override
            protected Type lookupType0(Type type, Provider from, Provider to) {
                return null;
            }

            @Override
            protected Type lookupField0(Type owner, String name, Type type, Provider from, Provider to) {
                return null;
            }

            @Override
            protected Type lookupMethod0(Type owner, String name, Type type, Provider from, Provider to) {
                return null;
            }
        },
        LUNAR {
            @Override
            protected Type lookupType0(Type type, Provider from, Provider to) {
                return null;
            }

            @Override
            protected Type lookupField0(Type owner, String name, Type type, Provider from, Provider to) {
                return null;
            }

            @Override
            protected Type lookupMethod0(Type owner, String name, Type type, Provider from, Provider to) {
                return null;
            }
        },
        MOJANG {
            @Override
            protected Type lookupType0(Type type, Provider from, Provider to) {
                return null;
            }

            @Override
            protected Type lookupField0(Type owner, String name, Type type, Provider from, Provider to) {
                return null;
            }

            @Override
            protected Type lookupMethod0(Type owner, String name, Type type, Provider from, Provider to) {
                return null;
            }
        };

        @Override
        public Type lookupType(Type type, Provider from, Provider to) {
            if (from == this) {
                return to.lookupType(type, this, to);
            }
            if (to == this) {
                if (ordinal() + 1 < from.ordinal()) {

                } else {

                }
                return null;
            }
            return from.lookupType(type, this, to);
        }

        @Override
        public Type lookupField(Type owner, String name, Type type, Provider from, Provider to) {
            if (to == this) {
                return null;
            }
            return from.lookupField(type, name, type, this, to);
        }

        @Override
        public Type lookupMethod(Type owner, String name, Type type, Provider from, Provider to) {
            if (from == this) {

            }
            if (to == this) {
                return null;
            }
            return from.lookupMethod(owner, name, type, this, to);
        }

        protected abstract Type lookupType0(Type type, Provider from, Provider to);
        protected abstract Type lookupField0(Type owner, String name, Type type, Provider from, Provider to);
        protected abstract Type lookupMethod0(Type owner, String name, Type type, Provider from, Provider to);
    }


    protected interface LookupTable {
        Type lookupType(Type type, Provider from, Provider to);
        Type lookupField(Type owner, String name, Type type, Provider from, Provider to);
        Type lookupMethod(Type owner, String name, Type type, Provider from, Provider to);
    }

    public static Type mapType(Type type, Provider from, Provider to) {
        switch (type.getSort()) {
            case Type.OBJECT:
                return Type.getType(String.format("L%s;", "asasasas"));
            case Type.ARRAY:
                return Type.getType(String.format("[L%s;", "asasasas"));
        }
        return null;
    }

    public static Type mapField(Type owner, String name,  Type type, Provider from, Provider to) {
        return from.lookupField(owner, name, type, from, to);
    }

    public static Type mapMethod(Type owner, String name, Type type, Provider from, Provider to) {
        return from.lookupMethod(owner, name, type, from, to);
    }

//    public String lunarToProguardClass(String name) {
//        return lunarProguardClassMap.get(name);
//    }
//
//    public String lunarToMojang(String name) {
//        return proguardMojangClassMap.get(lunarToProguardClass(name));
//    }
//
//    public String lunarToProguardDescriptor(String descriptor) {
//        return "";
//    }
//
//    public String l2mDesc(String descriptor) {
//        return "";
//    }
//
//    public String m2pClass(String name) {
//        return proguardMojangClassMap.inverse().get(name);
//    }
//
//    public String p2mClass(String name) {
//        return proguardMojangClassMap.get(name);
//    }
//
//    public String m2pDesc(String descriptor) {
//        return "";
//    }
//
//    public String p2mDesc(String descriptor) {
//        return "";
//    }
//
//    public String s2mField(String name) {
//        return seargeMojangFieldMap.get(name);
//    }
//
//    public String s2mMethod(String name) {
//        return seargeMojangMethodMap.get(name);
//    }
//
//    public String m2sMethod(String owner, String name, String desc) {
//        if (owner.indexOf('/') == -1) {
//            owner = p2mClass(owner);
//        } else if (owner.matches("net/minecraft/v([1-9].+)/")) {
//            owner = lunarToMojang(name);
//        }
//        return "";
//    }
//
//    public String m2sField(String owner, String name, String desc) {
//        if (owner.indexOf('/') == -1) {
//            owner = p2mClass(owner);
//        } else if (owner.matches("net/minecraft/v([1-9].+)/")) {
//            owner = lunarToMojang(name);
//        }
//        return "";
//    }
//
//    public String s2pField(String owner, String name, String desc) {
//        return proguardSeargeFieldMap.inverse().get(name);
//    }
//
//    public String s2pMethod(String owner, String name, String desc) {
//        return proguardSeargeMethodMap.inverse().get(name);
//    }

}
