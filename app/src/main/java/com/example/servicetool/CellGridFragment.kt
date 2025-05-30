package com.example.servicetool

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

private const val ARG_START_CELL = "start_cell"
private const val ARG_END_CELL = "end_cell"

class CellGridFragment : Fragment() {

    private var startCell: Int = 1
    private var endCell: Int = 8

    private lateinit var recyclerViewCells: RecyclerView
    private lateinit var cellAdapter: CellAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            startCell = it.getInt(ARG_START_CELL)
            endCell = it.getInt(ARG_END_CELL)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_cell_grid, container, false)
        recyclerViewCells = view.findViewById(R.id.recyclerViewCells)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cellDataList = (startCell..endCell).map { "Zelle $it" }
        cellAdapter = CellAdapter(cellDataList)

        recyclerViewCells.layoutManager = GridLayoutManager(context, 4)
        recyclerViewCells.adapter = cellAdapter
    }

    companion object {
        @JvmStatic
        fun newInstance(startCell: Int, endCell: Int) =
            CellGridFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_START_CELL, startCell)
                    putInt(ARG_END_CELL, endCell)
                }
            }
    }
}