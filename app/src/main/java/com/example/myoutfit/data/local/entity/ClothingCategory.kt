package com.example.myoutfit.data.local.entity

enum class ClothingCategory {
    // Prendas superiores
    T_SHIRTS,
    SHIRTS,
    TOPS,
    SWEATERS,

    // Prendas de cuerpo entero
    DRESSES,
    JUMPSUITS,

    // Prendas inferiores
    PANTS,
    JEANS,
    SKIRTS,
    SHORTS,
    BOTTOMS,

    // Abrigos y prendas exteriores
    OUTERWEAR,

    // Calzado
    FOOTWEAR,

    // Bolsos y complementos
    BAGS,
    ACCESSORIES;

    val displayName: String
        get() = when (this) {
            T_SHIRTS -> "Camisetas"
            SHIRTS -> "Camisas"
            TOPS -> "Tops"
            SWEATERS -> "Sudaderas y Suéteres"
            DRESSES -> "Vestidos"
            JUMPSUITS -> "Monos"
            PANTS -> "Pantalones"
            JEANS -> "Vaqueros"
            SKIRTS -> "Faldas"
            SHORTS -> "Shorts"
            BOTTOMS -> "Prendas inferiores"
            OUTERWEAR -> "Abrigos y Chaquetas"
            FOOTWEAR -> "Calzado"
            BAGS -> "Bolsos"
            ACCESSORIES -> "Accesorios"
        }

    val isTop: Boolean
        get() = this in setOf(T_SHIRTS, SHIRTS, TOPS, SWEATERS)

    val isBottom: Boolean
        get() = this in setOf(PANTS, JEANS, SKIRTS, SHORTS, BOTTOMS)

    val isOnePiece: Boolean
        get() = this in setOf(DRESSES, JUMPSUITS)

    val isOuterwear: Boolean
        get() = this == OUTERWEAR

    val isFootwear: Boolean
        get() = this == FOOTWEAR

    val isAccessory: Boolean
        get() = this in setOf(BAGS, ACCESSORIES)
}
